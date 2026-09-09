package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureForm;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureService;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Dos personas editando el mismo procedimiento.
 *
 * <p>Sin esta comprobacion, quien guarda segundo borra en silencio lo que escribio
 * el primero. Es el fallo mas caro de los que no dan error.
 */
class ProcedureConcurrentEditIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AdministrativeProcedureService servicio;

    private UUID procedimiento;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        procedimiento = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, requesting_area,
                                                      active, created_at, updated_at, version)
                VALUES (:id, :owner, 'ADM-CONC-2026', 'Original', true, :ahora, :ahora, 1)
                """).param("id", procedimiento).param("owner", id).param("ahora", ahora).update();
    }

    private AdministrativeProcedureForm form(String area, Long version) {
        return new AdministrativeProcedureForm(null, "ADM-CONC-2026", area, null, null,
                null, null, null, version);
    }

    private String areaActual() {
        return jdbc.sql("SELECT requesting_area FROM administrative_procedure WHERE id = :id")
                .param("id", procedimiento).query(String.class).single();
    }

    @Test
    @DisplayName("guardar con una version antigua no pisa el cambio de la otra persona")
    void versionObsoletaNoSobrescribe() {
        assertThat(servicio.editar(procedimiento, form("Cambio de la primera", 1L), jefa)
                .correcto()).isTrue();

        assertThatThrownBy(() -> servicio.editar(procedimiento, form("Cambio de la segunda", 1L), jefa))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);

        assertThat(areaActual()).as("debe conservarse el primer cambio")
                .isEqualTo("Cambio de la primera");
    }

    @Test
    @DisplayName("tras recargar con la version vigente, el segundo cambio si se guarda")
    void conVersionVigenteSiGuarda() {
        servicio.editar(procedimiento, form("Primero", 1L), jefa);

        long vigente = jdbc.sql("SELECT version FROM administrative_procedure WHERE id = :id")
                .param("id", procedimiento).query(Long.class).single();

        assertThat(servicio.editar(procedimiento, form("Segundo", vigente), jefa).correcto()).isTrue();
        assertThat(areaActual()).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("un formulario sin version se rechaza en vez de guardar a ciegas")
    void sinVersionSeRechaza() {
        assertThatThrownBy(() -> servicio.editar(procedimiento, form("Sin version", null), jefa))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);
        assertThat(areaActual()).isEqualTo("Original");
    }

    @Test
    @DisplayName("guardar sin cambiar nada no genera entrada de historial")
    void guardadoSinCambiosNoAudita() {
        servicio.editar(procedimiento, form("Original", 1L), jefa);

        Integer eventos = jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id = :id")
                .param("id", procedimiento).query(Integer.class).single();
        assertThat(eventos).isZero();
    }
}
