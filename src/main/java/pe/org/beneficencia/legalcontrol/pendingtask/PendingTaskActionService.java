package pe.org.beneficencia.legalcontrol.pendingtask;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Las cuatro acciones del dia a dia: cumplir, revertir, no cumplido y reprogramar.
 *
 * <p>Viven aparte del alta porque cada una tiene reglas propias, y son la parte de
 * esta funcionalidad donde se concentran los errores que no dan aviso.
 *
 * <p><b>Que significa «cumplido».</b> La verdad la lleva {@code completed_at}, no el
 * catalogo de estados. El catalogo lo administra la jefatura: alguien podria
 * renombrar «Cumplido», deshabilitarlo o no crearlo nunca, y el sistema seguiria
 * teniendo que saber si una tarea esta hecha. El estado del catalogo se actualiza
 * ademas, cuando existe uno cuyo nombre coincide, para que la pantalla diga lo que
 * el insumo espera (secciones 20 y 21); pero ninguna decision depende de el.
 */
@Service
public class PendingTaskActionService {

    private static final String ENTIDAD = "PENDING_TASK";
    private static final String NOMBRE_CUMPLIDO = "cumplido";
    private static final String NOMBRE_REPROGRAMADO = "reprogramado";
    private static final String NOMBRE_PENDIENTE = "pendiente";

    private final JdbcClient jdbc;
    private final PendingTaskRepository pendientes;
    private final PendingTaskAuthorization permisos;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public PendingTaskActionService(JdbcClient jdbc, PendingTaskRepository pendientes,
                                    PendingTaskAuthorization permisos,
                                    CalendarRepository calendario, DeadlineEvaluator plazos,
                                    AuditRecorder auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.pendientes = pendientes;
        this.permisos = permisos;
        this.calendario = calendario;
        this.plazos = plazos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** @return vacio si la accion se completo; el motivo del rechazo si no */
    @Transactional
    public Optional<String> marcarCumplido(UUID id, long version, CuentaActual actor) {
        var fila = bloquear(id, version, actor);
        if (fila.get("completed_at") != null) {
            return Optional.empty();   // ya estaba cumplido: no-op sin historial
        }

        Timestamp ahora = Timestamp.from(clock.instant());

        // NO se toca scheduled_for: asi, al revertir, la fecha no hay que
        // recuperarla de ningun sitio porque nunca se perdio (seccion 20.1).
        jdbc.sql("""
                UPDATE pending_task
                SET completed_at = :ahora,
                    pending_task_status_id = coalesce(:estado, pending_task_status_id),
                    updated_at = :ahora, version = version + 1
                WHERE id = :id
                """)
                .param("id", id).param("ahora", ahora)
                .param("estado", estadoLlamado(NOMBRE_CUMPLIDO))
                .update();

        registrar(id, "COMPLETE", actor, (UUID) fila.get("owner_id"),
                Map.of("completed", false), Map.of("completed", true), null);
        return Optional.empty();
    }

    /**
     * Deshace un cumplimiento.
     *
     * <p>El motivo es <b>obligatorio</b>: es la unica accion del sistema que
     * reescribe una afirmacion sobre trabajo ya declarado como hecho, y la que
     * alguien va a necesitar explicar meses despues (seccion 20.2).
     */
    @Transactional
    public Optional<String> revertirCumplimiento(UUID id, long version, String motivo,
                                                 CuentaActual actor) {
        if (motivo == null || motivo.isBlank()) {
            return Optional.of("Debe indicar un motivo para revertir un cumplimiento.");
        }

        var fila = bloquear(id, version, actor);
        if (fila.get("completed_at") == null) {
            throw new ErrorHandling.ConflictoDeEdicion(
                    "Este pendiente no esta cumplido, no hay nada que revertir.");
        }

        Timestamp ahora = Timestamp.from(clock.instant());

        jdbc.sql("""
                UPDATE pending_task
                SET completed_at = NULL,
                    pending_task_status_id = coalesce(:estado, pending_task_status_id),
                    updated_at = :ahora, version = version + 1
                WHERE id = :id
                """)
                .param("id", id).param("ahora", ahora)
                .param("estado", estadoLlamado(NOMBRE_PENDIENTE))
                .update();

        // Entrada NUEVA. La del cumplimiento no se borra ni se edita: queda el
        // rastro de que se marco como cumplido y de que despues se revirtio.
        registrar(id, "REVERT_COMPLETION", actor, (UUID) fila.get("owner_id"),
                Map.of("completed", true), Map.of("completed", false), motivo.strip());
        return Optional.empty();
    }

    /**
     * Declara que no se pudo, y lleva la tarea al siguiente dia habil.
     *
     * <p>Si falta cobertura de calendario <b>no elige fecha</b>: avisa y no cambia
     * nada. Reprogramar a un dia que resulto ser feriado es peor que no
     * reprogramar, porque nadie se entera hasta que llega el vencimiento.
     */
    @Transactional
    public Optional<String> declararNoCumplido(UUID id, long version, String motivo,
                                               CuentaActual actor) {
        var fila = bloquear(id, version, actor);
        if (fila.get("completed_at") != null) {
            throw new ErrorHandling.ConflictoDeEdicion(
                    "Este pendiente ya esta cumplido. Reviertalo antes de reprogramarlo.");
        }

        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = fila.get("scheduled_for") == null
                ? hoy
                : ((java.sql.Date) fila.get("scheduled_for")).toLocalDate();

        var siguiente = plazos.siguienteDiaHabil(desde, calendario.paraListado(hoy));
        if (siguiente.isEmpty()) {
            return Optional.of("No se puede calcular el siguiente dia habil: falta revisar el "
                    + "calendario de días no laborables. La fecha no se modificó.");
        }

        cambiarFecha(id, desde, siguiente.get(), "NOT_COMPLETED",
                (UUID) fila.get("owner_id"), motivo, actor);
        return Optional.empty();
    }

    /** Reprogramacion manual: la persona elige la fecha (seccion 22). */
    @Transactional
    public Optional<String> reprogramar(UUID id, long version, LocalDate nueva, String motivo,
                                        CuentaActual actor) {
        if (nueva == null) {
            return Optional.of("Indique la fecha a la que quiere reprogramarlo.");
        }
        var fila = bloquear(id, version, actor);

        LocalDate anterior = fila.get("scheduled_for") == null
                ? null
                : ((java.sql.Date) fila.get("scheduled_for")).toLocalDate();

        if (nueva.equals(anterior)) {
            return Optional.empty();   // no-op: sin cambio no hay historial
        }

        cambiarFecha(id, anterior, nueva, "RESCHEDULE",
                (UUID) fila.get("owner_id"), motivo, actor);
        return Optional.empty();
    }

    // ---------------------------------------------------------------- interno

    /** Bloquea la fila, comprueba la version y revalida el permiso, en ese orden. */
    private Map<String, Object> bloquear(UUID id, long version, CuentaActual actor) {
        var fila = pendientes.bloquearParaActuar(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("pendiente inexistente"));

        // El permiso se revalida DESPUES de bloquear: una revocacion concurrente
        // debe impedir el guardado posterior.
        if (!permisos.puedeActuar(actor, (UUID) fila.get("owner_id"))) {
            throw new ErrorHandling.SinPermiso("no puede actuar sobre pendientes ajenos");
        }
        if (((Number) fila.get("version")).longValue() != version) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modificó este pendiente");
        }
        return fila;
    }

    private void cambiarFecha(UUID id, LocalDate anterior, LocalDate nueva, String accion,
                              UUID responsable, String motivo, CuentaActual actor) {
        Timestamp ahora = Timestamp.from(clock.instant());

        jdbc.sql("""
                UPDATE pending_task
                SET scheduled_for = :nueva,
                    pending_task_status_id = coalesce(:estado, pending_task_status_id),
                    updated_at = :ahora, version = version + 1
                WHERE id = :id
                """)
                .param("id", id).param("nueva", nueva).param("ahora", ahora)
                .param("estado", estadoLlamado(NOMBRE_REPROGRAMADO))
                .update();

        registrar(id, accion, actor, responsable,
                mapaFecha(anterior), mapaFecha(nueva),
                motivo == null || motivo.isBlank() ? null : motivo.strip());
    }

    private Map<String, Object> mapaFecha(LocalDate fecha) {
        return java.util.Collections.singletonMap("scheduledFor",
                fecha == null ? null : fecha.toString());
    }

    private void registrar(UUID id, String accion, CuentaActual actor, UUID responsable,
                           Map<String, Object> antes, Map<String, Object> despues, String motivo) {
        auditoria.registrar(ENTIDAD, id, accion, actor.id(), responsable, antes, despues, motivo);
    }

    /**
     * Busca en el catalogo un estado con ese nombre, sin distinguir caja.
     *
     * <p>Devuelve nulo si no existe, y entonces el estado del pendiente se queda
     * como estaba: la accion se completa igual. El catalogo lo administra la
     * jefatura y puede no tener ese valor.
     */
    private UUID estadoLlamado(String nombre) {
        return jdbc.sql("""
                SELECT id FROM pending_task_status
                WHERE lower(btrim(name)) = :nombre LIMIT 1
                """).param("nombre", nombre).query(UUID.class).optional().orElse(null);
    }
}
