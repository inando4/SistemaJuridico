package pe.org.beneficencia.legalcontrol.assignment;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.audit.AuditRecorder.CambioDeResponsable;

/**
 * Las sentencias del cambio de responsable.
 *
 * <p>Tres por reasignacion, <b>con independencia de cuantos pendientes cuelguen</b>:
 * la foto previa, el cambio en bloque y el del propio expediente. Con cincuenta
 * pendientes son las mismas tres que con cinco.
 *
 * <p><b>Por que hace falta la foto previa.</b> PostgreSQL 17 no tiene
 * {@code RETURNING OLD.*} —llego en la 18—, y el historial necesita el responsable
 * que cada pendiente tenia <em>antes</em>. No vale suponer que era el del
 * expediente: nada impide que una abogada registre un pendiente colgado del
 * expediente de otro y quede a su nombre.
 */
@Repository
public class ReassignmentRepository {

    /** De que cuelga el pendiente. La columna cambia; la logica, no. */
    public enum Vinculo {
        JUDICIAL("judicial_case_id"),
        ADMINISTRATIVO("administrative_procedure_id");

        final String columna;

        Vinculo(String columna) {
            this.columna = columna;
        }
    }

    private final JdbcClient jdbc;

    public ReassignmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Quien es responsable de que, antes de tocar nada.
     *
     * <p>Solo los que van a cambiar: el {@code owner_id <> :nuevo} deja fuera los que
     * ya son del destino, que no son un cambio efectivo y no deben generar historial
     * (principio VII). La comprobacion es <b>por registro</b>: un expediente que ya
     * es de B puede tener pendientes de A, y esos si cambian.
     */
    public List<CambioDeResponsable> pendientesQueCambiarian(Vinculo vinculo, UUID expediente,
                                                             UUID nuevo) {
        return jdbc.sql("""
                SELECT id, owner_id FROM pending_task
                WHERE %s = :expediente AND owner_id <> :nuevo
                ORDER BY id
                """.formatted(vinculo.columna))
                .param("expediente", expediente).param("nuevo", nuevo)
                .query((fila, n) -> new CambioDeResponsable(
                        fila.getObject("id", UUID.class),
                        fila.getObject("owner_id", UUID.class)))
                .list();
    }

    /** Los pendientes del expediente, en bloque. Devuelve cuantos cambiaron. */
    public int moverPendientes(Vinculo vinculo, UUID expediente, UUID nuevo, Instant ahora) {
        return jdbc.sql("""
                UPDATE pending_task
                SET owner_id = :nuevo, updated_at = :ahora, version = version + 1
                WHERE %s = :expediente AND owner_id <> :nuevo
                """.formatted(vinculo.columna))
                .param("nuevo", nuevo).param("expediente", expediente)
                .param("ahora", Timestamp.from(ahora))
                .update();
    }

    /**
     * El expediente, con comprobacion de version.
     *
     * @return false si la version no coincide (otra persona lo modifico) o si ya era
     *         del destino, casos que el servicio distingue consultando el estado
     */
    public boolean moverExpediente(String tabla, UUID id, UUID nuevo, long version,
                                   Instant ahora) {
        // El nombre de tabla es constante del propio codigo, nunca entrada externa.
        return jdbc.sql("""
                UPDATE %s
                SET owner_id = :nuevo, updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version AND owner_id <> :nuevo
                """.formatted(tabla))
                .param("nuevo", nuevo).param("id", id).param("version", version)
                .param("ahora", Timestamp.from(ahora))
                .update() == 1;
    }

    /** Un pendiente suelto, con comprobacion de version. */
    public boolean moverPendienteSuelto(UUID id, UUID nuevo, long version, Instant ahora) {
        return jdbc.sql("""
                UPDATE pending_task
                SET owner_id = :nuevo, updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version AND owner_id <> :nuevo
                  AND judicial_case_id IS NULL AND administrative_procedure_id IS NULL
                """)
                .param("nuevo", nuevo).param("id", id).param("version", version)
                .param("ahora", Timestamp.from(ahora))
                .update() == 1;
    }

    /** Estado actual de un expediente: responsable y version, para decidir y avisar. */
    public java.util.Optional<EstadoActual> estadoDelExpediente(String tabla, UUID id) {
        return jdbc.sql("SELECT owner_id, version FROM " + tabla + " WHERE id = :id")
                .param("id", id)
                .query((fila, n) -> new EstadoActual(
                        fila.getObject("owner_id", UUID.class), fila.getLong("version")))
                .optional();
    }

    /** Estado actual de un pendiente, con su vinculo, para poder rechazar los vinculados. */
    public java.util.Optional<EstadoDePendiente> estadoDelPendiente(UUID id) {
        return jdbc.sql("""
                SELECT owner_id, version, judicial_case_id, administrative_procedure_id
                FROM pending_task WHERE id = :id
                """)
                .param("id", id)
                .query((fila, n) -> new EstadoDePendiente(
                        fila.getObject("owner_id", UUID.class),
                        fila.getLong("version"),
                        fila.getObject("judicial_case_id", UUID.class),
                        fila.getObject("administrative_procedure_id", UUID.class)))
                .optional();
    }

    public record EstadoActual(UUID responsable, long version) {
    }

    public record EstadoDePendiente(UUID responsable, long version,
                                    UUID expedienteJudicial, UUID expedienteAdministrativo) {
        public boolean estaVinculado() {
            return expedienteJudicial != null || expedienteAdministrativo != null;
        }
    }
}
