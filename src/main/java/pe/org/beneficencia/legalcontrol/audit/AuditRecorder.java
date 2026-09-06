package pe.org.beneficencia.legalcontrol.audit;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

// Spring Boot 4 trae Jackson 3: el paquete es tools.jackson, no com.fasterxml.
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe la evidencia de una modificacion.
 *
 * <p><b>No abre transaccion propia a proposito.</b> Se une a la de quien llama,
 * de modo que el cambio y su evidencia se guardan juntos o no se guarda ninguno.
 * Si esta escritura falla, la excepcion sube y arrastra la modificacion: es la
 * unica forma de que «atomico» signifique algo y no sea una intencion.
 *
 * <p>Solo modificaciones efectivas. Consultar no escribe nada, y guardar sin
 * cambios tampoco: {@link #registrar} lo comprueba antes de insertar.
 *
 * <p>Nunca guarda contrasenas, hashes ni codigos. De las credenciales solo queda
 * el hecho de que se establecieron o reemplazaron.
 */
@Component
public class AuditRecorder {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public AuditRecorder(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param actorId quien ejecuta el cambio
     * @param ownerId responsable del registro en ese momento, que puede ser otra
     *                persona: la jefa modifica expedientes ajenos
     * @return el id del evento escrito, o vacio si no habia nada que registrar
     */
    public java.util.Optional<UUID> registrar(String entityType, UUID entityId, String action,
                             UUID actorId, UUID ownerId,
                             Map<String, Object> antes, Map<String, Object> despues,
                             String motivo) {

        if (Objects.equals(antes, despues)) {
            return java.util.Optional.empty();   // no-op: no se inventa historial
        }

        UUID eventoId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO audit_event
                    (id, entity_type, entity_id, action, actor_id, owner_id,
                     occurred_at, before_values, after_values, reason)
                VALUES
                    (:id, :entityType, :entityId, :action, :actorId, :ownerId,
                     :occurredAt, CAST(:antes AS jsonb), CAST(:despues AS jsonb), :motivo)
                """)
                .param("id", eventoId)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("action", action)
                .param("actorId", actorId)
                .param("ownerId", ownerId)
                .param("occurredAt", java.sql.Timestamp.from(clock.instant()))
                .param("antes", aJson(antes))
                .param("despues", aJson(despues))
                .param("motivo", motivo)
                .update();
        return java.util.Optional.of(eventoId);
    }

    /**
     * Ata un evento a los estados procesales implicados, antes y despues.
     *
     * <p>Esto es lo que impide borrar mas adelante un estado que alguna vez se
     * uso: si desapareciera, este historial dejaria de poder explicarse.
     */
    public void referenciarEstados(UUID eventoId, UUID... estados) {
        for (UUID estado : estados) {
            if (estado == null) {
                continue;
            }
            jdbc.sql("""
                    INSERT INTO case_history_status_reference (audit_event_id, procedural_status_id)
                    VALUES (:evento, :estado)
                    ON CONFLICT DO NOTHING
                    """).param("evento", eventoId).param("estado", estado).update();
        }
    }

    private String aJson(Map<String, Object> valores) {
        if (valores == null) {
            return null;    // alta o eliminacion: un lado puede ser ausencia
        }
        try {
            return json.writeValueAsString(valores);
        } catch (JacksonException e) {
            // Si no se puede serializar la evidencia, no se guarda el cambio.
            throw new IllegalStateException("No fue posible construir la evidencia", e);
        }
    }
}
