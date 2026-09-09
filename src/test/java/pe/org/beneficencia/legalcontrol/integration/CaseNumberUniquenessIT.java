package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseForm;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseService;

/**
 * El numero de expediente identifica al expediente en toda el area.
 *
 * <p>La unicidad tiene que aguantar tres cosas que se olvidan: mayusculas y
 * espacios distintos, registros de <b>otro responsable</b>, y registros
 * <b>ocultos</b>. Un duplicado creado por cualquiera de esas vias significa dos
 * fichas del mismo expediente fisico, que es exactamente el desorden del que
 * huyen al dejar el Excel.
 */
class CaseNumberUniquenessIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JudicialCaseService servicio;

    private UUID abogadoA;
    private UUID abogadoB;

    @BeforeEach
    void cuentas() {
        SesionDePrueba.limpiar(jdbc);
        abogadoA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
    }

    private JudicialCaseForm con(String numero) {
        return new JudicialCaseForm(null, numero, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private int expedientes() {
        return jdbc.sql("SELECT count(*) FROM judicial_case").query(Integer.class).single();
    }

    @Test
    @DisplayName("el mismo numero con distinta caja o espacios se rechaza")
    void normalizacion() {
        assertThat(servicio.crear(con("EXP-100-2026"), abogadoA).correcto()).isTrue();

        assertThat(servicio.crear(con("exp-100-2026"), abogadoA).correcto()).isFalse();
        assertThat(servicio.crear(con("  EXP-100-2026  "), abogadoA).correcto()).isFalse();
        assertThat(expedientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("un numero ya usado por otro abogado tambien se rechaza")
    void duplicadoEntreResponsables() {
        assertThat(servicio.crear(con("EXP-200-2026"), abogadoA).correcto()).isTrue();

        var resultado = servicio.crear(con("EXP-200-2026"), abogadoB);

        assertThat(resultado.correcto()).isFalse();
        assertThat(resultado.errores().get("caseNumber")).contains("Ya existe");
        assertThat(expedientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("un numero usado por un expediente oculto tambien se rechaza")
    void duplicadoContraOculto() {
        assertThat(servicio.crear(con("EXP-300-2026"), abogadoA).correcto()).isTrue();
        jdbc.sql("UPDATE judicial_case SET active = false").update();

        assertThat(servicio.crear(con("EXP-300-2026"), abogadoB).correcto()).isFalse();
        assertThat(expedientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("dos altas simultaneas del mismo numero: solo prospera una")
    void altasConcurrentes() throws Exception {
        int intentos = 8;
        try (ExecutorService pool = Executors.newFixedThreadPool(intentos)) {
            List<Callable<Boolean>> tareas = java.util.stream.IntStream.range(0, intentos)
                    .<Callable<Boolean>>mapToObj(i -> () ->
                            servicio.crear(con("EXP-CARRERA-2026"), abogadoA).correcto())
                    .toList();

            long exitos = pool.invokeAll(tareas).stream()
                    .filter(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            return false;
                        }
                    }).count();

            assertThat(exitos).as("solo una de las altas simultaneas debe prosperar").isEqualTo(1);
        }
        assertThat(expedientes()).isEqualTo(1);
    }
}
