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
                       e.reason,
                       destino.name AS new_owner_name
                FROM audit_event e
                JOIN app_user autor       ON autor.id = e.actor_id
                JOIN app_user responsable ON responsable.id = e.owner_id
                -- El responsable NUEVO de una reasignacion. Se resuelve aqui y no
                -- con una consulta aparte por entrada: seria un N+1 en la pantalla
                -- que mas entradas tiene. Sale nulo en las demas acciones, que no
                -- llevan ownerId en la evidencia.
                LEFT JOIN app_user destino
                       ON e.action = 'REASSIGN'
                      AND destino.id = (e.after_values ->> 'ownerId')::uuid
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
            String beforeValues, String afterValues, String reason,
            String newOwnerName) {

        /** true cuando quien hizo el cambio no era el responsable del registro. */
        public boolean intervencionAjena() {
            return !actorId.equals(ownerId);
        }

        /** Fecha programada antes del cambio, o null si esta entrada no la movio. */
        public String fechaAnterior() {
            return valorDeFecha(beforeValues);
        }

        /** Fecha programada despues del cambio, o null si esta entrada no la movio. */
        public String fechaNueva() {
            return valorDeFecha(afterValues);
        }

        /** true cuando esta entrada es un cambio de responsable. */
        public boolean esReasignacion() {
            return "REASSIGN".equals(action);
        }

        /**
         * Responsable anterior y nuevo de una reasignacion, o null si no lo es.
         *
         * <p>Se resuelven al pintar, no al guardar, por lo mismo que las fechas: la
         * evidencia conserva los identificadores y la redaccion puede cambiar sin
         * reescribir un historial que ademas es inmutable.
         */
        /**
         * Nombre del responsable anterior de una reasignacion.
         *
         * <p>Es {@code ownerName} sin mas: la entrada se escribe con
         * {@code owner_id} = responsable en el momento del cambio, que en una
         * reasignacion es justamente el que la pierde.
         */
        public String responsableAnterior() {
            return esReasignacion() ? ownerName : null;
        }

        /** Nombre del responsable nuevo, resuelto en la misma consulta. */
        public String responsableNuevo() {
            return newOwnerName;
        }

        /**
         * Saca {@code scheduledFor} de la evidencia en JSON.
         *
         * <p>Se lee al pintar en vez de guardar la frase compuesta: la evidencia
         * conserva los datos, y la redaccion puede cambiar sin reescribir el
         * historial, que ademas es inmutable.
         */
        private static String valorDeFecha(String json) {
            return valorDe(json, "scheduledFor");
        }

        /** El valor de una clave de texto de la evidencia, sin traer un parser entero. */
        private static String valorDe(String json, String nombre) {
            if (json == null) {
                return null;
            }
            int clave = json.indexOf("\"" + nombre + "\"");
            if (clave < 0) {
                return null;
            }
            int abre = json.indexOf('"', json.indexOf(':', clave) + 1);
            if (abre < 0) {
                return null;
            }
            int cierra = json.indexOf('"', abre + 1);
            return cierra < 0 ? null : json.substring(abre + 1, cierra);
        }
    }
}
