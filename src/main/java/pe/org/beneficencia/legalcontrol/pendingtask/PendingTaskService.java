package pe.org.beneficencia.legalcontrol.pendingtask;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Alta y edicion de pendientes.
 *
 * <p>Las acciones con reglas (cumplir, revertir, no cumplido, reprogramar) viven
 * aparte en su propio servicio, porque tienen reglas propias que no
 * comparten con el alta.
 */
@Service
public class PendingTaskService {

    static final String ENTIDAD = "PENDING_TASK";

    private final PendingTaskRepository pendientes;
    private final PendingTaskValidator validador;
    private final PendingTaskAuthorization permisos;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public PendingTaskService(PendingTaskRepository pendientes, PendingTaskValidator validador,
                              PendingTaskAuthorization permisos, AuditRecorder auditoria,
                              Clock clock) {
        this.pendientes = pendientes;
        this.validador = validador;
        this.permisos = permisos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public record Resultado(UUID id, Map<String, String> errores) {
        public boolean correcto() {
            return errores.isEmpty();
        }
        static Resultado con(Map<String, String> errores) {
            return new Resultado(null, errores);
        }
    }

    @Transactional
    public Resultado crear(PendingTaskForm form, UUID responsable) {
        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }

        UUID id = pendientes.insertar(form, responsable, clock.instant());

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("title", form.title().strip());
        despues.put("ownerId", responsable.toString());
        if (form.scheduledFor() != null && !form.scheduledFor().isBlank()) {
            despues.put("scheduledFor", form.scheduledFor().strip());
        }
        if (form.deadline() != null && !form.deadline().isBlank()) {
            despues.put("deadline", form.deadline().strip());
        }

        auditoria.registrar(ENTIDAD, id, "CREATE", responsable, responsable, null, despues, null)
                .ifPresent(evento -> auditoria.referenciarCatalogosDePendiente(evento,
                        form.pendingTaskTypeId(), form.priorityId(), form.pendingTaskStatusId()));

        return new Resultado(id, Map.of());
    }

    @Transactional
    public Resultado editar(UUID id, PendingTaskForm form, CuentaActual actor) {
        var fila = pendientes.bloquearParaActuar(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("pendiente inexistente"));

        UUID responsable = (UUID) fila.get("owner_id");
        long versionActual = ((Number) fila.get("version")).longValue();

        // Se revalida DESPUES de bloquear: si entre la comprobacion y la escritura
        // alguien revocara el acceso, la comprobacion previa ya no valdria.
        if (!permisos.puedeActuar(actor, responsable)) {
            throw new ErrorHandling.SinPermiso("no puede actuar sobre pendientes ajenos");
        }

        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }
        if (form.version() == null || form.version() != versionActual) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este pendiente");
        }

        var antesValores = pendientes.porId(id).orElseThrow();
        Map<String, Object> antes = instantanea(antesValores);

        if (!pendientes.actualizar(id, form, versionActual, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este pendiente");
        }

        var despuesValores = pendientes.porId(id).orElseThrow();

        auditoria.registrar(ENTIDAD, id, "UPDATE", actor.id(), responsable,
                        antes, instantanea(despuesValores), null)
                .ifPresent(evento -> auditoria.referenciarCatalogosDePendiente(evento,
                        antesValores.pendingTaskTypeId(), antesValores.priorityId(),
                        antesValores.pendingTaskStatusId(), form.pendingTaskTypeId(),
                        form.priorityId(), form.pendingTaskStatusId()));

        return new Resultado(id, Map.of());
    }

    /** Campos que se comparan para decidir si hubo cambio y que se registra. */
    static Map<String, Object> instantanea(PendingTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", t.title());
        m.put("description", t.description());
        m.put("typeId", texto(t.pendingTaskTypeId()));
        m.put("priorityId", texto(t.priorityId()));
        m.put("statusId", texto(t.pendingTaskStatusId()));
        m.put("judicialCaseId", texto(t.judicialCaseId()));
        m.put("administrativeProcedureId", texto(t.administrativeProcedureId()));
        m.put("receivedAt", texto(t.receivedAt()));
        m.put("scheduledFor", texto(t.scheduledFor()));
        m.put("deadline", texto(t.deadline()));
        m.put("outputDocumentType", t.outputDocumentType());
        m.put("outputDocumentNumber", t.outputDocumentNumber());
        m.put("notes", t.notes());
        return m;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
