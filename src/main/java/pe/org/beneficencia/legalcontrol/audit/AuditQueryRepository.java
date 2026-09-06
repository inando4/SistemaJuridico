package pe.org.beneficencia.legalcontrol.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Lectura del historial. Solo lectura: aqui no hay forma de modificarlo, y la
 * base tampoco se lo permitiria a la aplicacion.
 */
@Repository
public class AuditQueryRepository {

    private final JdbcClient jdbc;

    public AuditQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Historial de una entidad, del cambio mas reciente al mas antiguo.
     *
     * <p>Distingue autor de responsable: cuando la jefa modifica un expediente
     * ajeno, el historial debe decir quien lo hizo y de quien era.
     */
    public List<EntradaHistorial> deEntidad(String tipo, UUID id, Paging pagina) {
        return jdbc.sql("""
                SELECT e.occurred_at, e.action,
                       autor.name AS actor_name,
                       responsable.name AS owner_name,
                       e.actor_id, e.owner_id,
                       e.before_values::text AS before_values,
                       e.after_values::text  AS after_values,
                       e.reason
                FROM audit_event e
                JOIN app_user autor       ON autor.id = e.actor_id
                JOIN app_user responsable ON responsable.id = e.owner_id
                WHERE e.entity_type = :tipo AND e.entity_id = :id
                ORDER BY e.occurred_at DESC, e.id DESC
                LIMIT :limite OFFSET :salto
                """)
                .param("tipo", tipo).param("id", id)
                .param("limite", pagina.limitConSondeo())
                .param("salto", pagina.offset())
                .query(EntradaHistorial.class)
                .list();
    }

    public record EntradaHistorial(
            Instant occurredAt, String action,
            String actorName, String ownerName,
            UUID actorId, UUID ownerId,
            String beforeValues, String afterValues, String reason) {

        /** true cuando quien hizo el cambio no era el responsable del registro. */
        public boolean intervencionAjena() {
            return !actorId.equals(ownerId);
        }
    }
}
