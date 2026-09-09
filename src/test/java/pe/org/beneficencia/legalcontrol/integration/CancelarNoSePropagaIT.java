package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseService;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskActionService;

/**
 * Cancelar no se propaga: ni de un pendiente a su expediente, ni al reves (RF-008).
 *
 * <p>La relacion invita a suponer lo contrario. Una cascada retiraria trabajo de otras
 * personas sin que nadie lo hubiera decidido, y desde la 008 la ficha del expediente
 * muestra pendientes de cualquier responsable.
 */
class CancelarNoSePropagaIT extends PostgresIntegrationTest {

    @Autowired private PendingTaskActionService acciones;
    @Autowired private JudicialCaseService expedientes;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private CuentaActual jefa;
    private UUID expediente;
    private UUID unPendiente;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        UUID otra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        jefa = new CuentaActual(jefaId, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        expediente = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-CASCADA-2026', true, :a, :a, 1)
                """).param("id", expediente).param("o", jefaId)
                .param("a", Timestamp.from(Instant.now())).update();

        // Doce pendientes de OTRA persona colgando del expediente.
        for (int i = 0; i < 12; i++) {
            UUID id = UUID.randomUUID();
            if (i == 0) {
                unPendiente = id;
            }
            Timestamp ahora = Timestamp.from(Instant.now());
            jdbc.sql("""
                    INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                              registered_at, active, created_at, updated_at,
                                              version)
                    VALUES (:id, :o, :t, :j, :hoy, true, :ts, :ts, 1)
                    """)
                    .param("id", id).param("o", otra).param("t", "Actuacion " + i)
                    .param("j", expediente).param("hoy", LocalDate.now())
                    .param("ts", ahora).update();
        }
    }

    private long versionExpediente() {
        return jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(Long.class).single();
    }

    private int pendientesActivos() {
        return jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE judicial_case_id = :id AND active = true
                """).param("id", expediente).query(Integer.class).single();
    }

    @Test
    @DisplayName("ocultar un expediente NO cancela sus pendientes (RF-008)")
    void ocultarNoCancelaEnCascada() {
        expedientes.cambiarVisibilidad(expediente, false, versionExpediente(), jefa);

        assertThat(jdbc.sql("SELECT active FROM judicial_case WHERE id = :id")
                        .param("id", expediente).query(Boolean.class).single())
                .isFalse();
        assertThat(pendientesActivos())
                .as("los doce siguen en la lista de trabajo de quien los tenga")
                .isEqualTo(12);
    }

    @Test
    @DisplayName("cancelar un pendiente NO toca su expediente (RF-008)")
    void cancelarNoTocaElExpediente() {
        long versionAntes = versionExpediente();
        long versionDelPendiente = jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", unPendiente).query(Long.class).single();

        acciones.cancelar(unPendiente, versionDelPendiente, jefa);

        assertThat(jdbc.sql("SELECT active FROM judicial_case WHERE id = :id")
                        .param("id", expediente).query(Boolean.class).single()).isTrue();
        assertThat(versionExpediente())
                .as("ni siquiera se toca su version: el expediente no se ha modificado")
                .isEqualTo(versionAntes);
        assertThat(pendientesActivos()).isEqualTo(11);
    }

    @Test
    @DisplayName("volver a mostrar el expediente tampoco resucita nada")
    void volverAMostrarNoResucita() {
        long v = jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", unPendiente).query(Long.class).single();
        acciones.cancelar(unPendiente, v, jefa);

        expedientes.cambiarVisibilidad(expediente, false, versionExpediente(), jefa);
        expedientes.cambiarVisibilidad(expediente, true, versionExpediente(), jefa);

        assertThat(pendientesActivos())
                .as("el que se cancelo a proposito sigue cancelado")
                .isEqualTo(11);
    }
}
