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
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseForm;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseService;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Dos personas editando el mismo expediente.
 *
 * <p>Sin esta comprobacion, quien guarda segundo borra en silencio lo que escribio
 * el primero, y nadie se entera. Es el fallo mas caro de los que no dan error.
 */
class ConcurrentEditIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JudicialCaseService servicio;

    private UUID expediente;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        expediente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-CONC-2026', 'Original', true, :ahora, :ahora, 1)
                """)
                .param("id", expediente).param("owner", id).param("ahora", ahora).update();
    }

    private JudicialCaseForm form(String materia, Long version) {
        return new JudicialCaseForm(null, "EXP-CONC-2026", null, null, materia, null,
                null, null, null, null, null, null, null, null, true, version);
    }

    private String materiaActual() {
        return jdbc.sql("SELECT subject FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(String.class).single();
    }

    @Test
    @DisplayName("guardar con una version antigua no pisa el cambio de la otra persona")
    void versionObsoletaNoSobrescribe() {
        // Ambas abren el formulario con la version 1.
        JudicialCaseForm primera = form("Cambio de la primera persona", 1L);
        JudicialCaseForm segunda = form("Cambio de la segunda persona", 1L);

        assertThat(servicio.editar(expediente, primera, jefa).correcto()).isTrue();

        assertThatThrownBy(() -> servicio.editar(expediente, segunda, jefa))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);

        assertThat(materiaActual()).as("debe conservarse el primer cambio, no el segundo")
                .isEqualTo("Cambio de la primera persona");
    }

    @Test
    @DisplayName("tras recargar con la version vigente, el segundo cambio si se guarda")
    void conVersionVigenteSiGuarda() {
        servicio.editar(expediente, form("Primero", 1L), jefa);

        long vigente = jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(Long.class).single();

        assertThat(servicio.editar(expediente, form("Segundo", vigente), jefa).correcto()).isTrue();
        assertThat(materiaActual()).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("un formulario sin version se rechaza en vez de guardar a ciegas")
    void sinVersionSeRechaza() {
        assertThatThrownBy(() -> servicio.editar(expediente, form("Sin version", null), jefa))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);
        assertThat(materiaActual()).isEqualTo("Original");
    }

    @Test
    @DisplayName("guardar sin cambiar nada no genera entrada de historial")
    void guardadoSinCambiosNoAudita() {
        servicio.editar(expediente, form("Original", 1L), jefa);

        Integer eventos = jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id = :id")
                .param("id", expediente).query(Integer.class).single();
        assertThat(eventos).isZero();
    }
}
