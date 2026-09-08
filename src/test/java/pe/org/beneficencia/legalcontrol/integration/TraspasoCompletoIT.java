package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * Nadie deja el area con trabajo inmovilizado (CE-010).
 *
 * <p>Es la comprobacion que cierra la pregunta que se le hizo al cliente. Entre la
 * reasignacion de expedientes judiciales, la de administrativos y la individual de
 * pendientes sueltos no puede quedar <b>ningun</b> registro sin via de traspaso: si
 * quedara uno, seria precisamente el que nadie puede tocar cuando su responsable ya
 * no esta.
 */
class TraspasoCompletoIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private CuentaActual jefa;
    private UUID seVa;
    private UUID seQueda;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        UUID jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        seVa = SesionDePrueba.crearCuenta(jdbc, encoder, "seva@ejemplo.test", "LAWYER");
        seQueda = SesionDePrueba.crearCuenta(jdbc, encoder, "sequeda@ejemplo.test", "LAWYER");
        jefa = ReasignacionIT.cuenta(jefaId, "HEAD");
    }

    private int registrosDe(UUID quien) {
        Integer n = jdbc.sql("""
                SELECT (SELECT count(*) FROM judicial_case WHERE owner_id = :q)
                     + (SELECT count(*) FROM administrative_procedure WHERE owner_id = :q)
                     + (SELECT count(*) FROM pending_task WHERE owner_id = :q)
                """).param("q", quien).query(Integer.class).single();
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("todo lo de quien deja el area se puede traspasar, sin excepcion")
    void nadaQuedaSinViaDeTraspaso() {
        // Un expediente judicial con pendientes, uno administrativo, y sueltos.
        List<UUID> judicial = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, seVa, 2, 1);
        DatosSinteticos.sembrarAdministrativos(jdbc, seVa, 1, 1);
        UUID administrativo = jdbc.sql("SELECT id FROM administrative_procedure LIMIT 1")
                .query(UUID.class).single();

        var ahora = java.sql.Timestamp.from(java.time.Instant.now());
        UUID tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "T", 1,
                seVa, ahora).get(0);
        UUID prio = DatosSinteticos.sembrarCatalogo(jdbc, "priority", "P", 1, seVa, ahora).get(0);
        UUID est = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "E", 1,
                seVa, ahora).get(0);
        UUID suelto1 = DatosSinteticos.pendienteSuelto(jdbc, seVa, tipo, prio, est,
                "Suelto uno", LocalDate.now().plusDays(3), LocalDate.now().minusDays(1));
        UUID suelto2 = DatosSinteticos.pendienteSuelto(jdbc, seVa, tipo, prio, est,
                "Suelto dos", null, LocalDate.now().minusDays(40));

        assertThat(registrosDe(seVa)).as("hay trabajo que traspasar").isEqualTo(7);

        // Las tres vias, que es todo lo que el sistema ofrece.
        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, judicial.get(0), seQueda,
                version("judicial_case", judicial.get(0)), jefa);
        reasignaciones.reasignarExpediente(Tipo.ADMINISTRATIVO, administrativo, seQueda,
                version("administrative_procedure", administrativo), jefa);
        reasignaciones.reasignarPendienteSuelto(suelto1, seQueda,
                version("pending_task", suelto1), jefa);
        reasignaciones.reasignarPendienteSuelto(suelto2, seQueda,
                version("pending_task", suelto2), jefa);

        assertThat(registrosDe(seVa))
                .as("si quedara uno, seria justo el que nadie puede tocar cuando se vaya")
                .isZero();
        assertThat(registrosDe(seQueda)).isEqualTo(7);
    }

    private long version(String tabla, UUID id) {
        return jdbc.sql("SELECT version FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }
}
