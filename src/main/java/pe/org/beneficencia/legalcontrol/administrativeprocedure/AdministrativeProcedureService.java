package pe.org.beneficencia.legalcontrol.administrativeprocedure;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Alta y edicion de procedimientos administrativos.
 *
 * <p>El cambio y su evidencia van en la misma transaccion: si la auditoria falla,
 * el procedimiento no se guarda.
 */
@Service
public class AdministrativeProcedureService {

    private static final String ENTIDAD = "ADMINISTRATIVE_PROCEDURE";

    private final AdministrativeProcedureRepository procedimientos;
    private final AdministrativeProcedureValidator validador;
    private final ProcedureAuthorization permisos;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public AdministrativeProcedureService(AdministrativeProcedureRepository procedimientos,
                                          AdministrativeProcedureValidator validador,
                                          ProcedureAuthorization permisos,
                                          AuditRecorder auditoria, Clock clock) {
        this.procedimientos = procedimientos;
        this.validador = validador;
        this.permisos = permisos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public record Resultado(UUID id, Map<String, String> errores, String advertencia) {
        public boolean correcto() {
            return errores.isEmpty();
        }
        static Resultado con(Map<String, String> errores) {
            return new Resultado(null, errores, null);
        }
    }

    @Transactional
    public Resultado crear(AdministrativeProcedureForm form, UUID responsable) {
        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }

        String numero = form.fileNumber().strip();
        if (procedimientos.numeroYaUsado(numero)) {
            errores.put("fileNumber", "Ya existe un procedimiento con ese numero, aunque sea "
                    + "de otra persona o este oculto.");
            return Resultado.con(errores);
        }

        UUID id;
        try {
            id = procedimientos.insertar(form, responsable, clock.instant());
        } catch (DuplicateKeyException carrera) {
            // Dos altas simultaneas: decide la restriccion unica de la base.
            errores.put("fileNumber", "Ya existe un procedimiento con ese numero.");
            return Resultado.con(errores);
        }

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("fileNumber", numero);
        despues.put("ownerId", responsable.toString());
        if (form.requestingArea() != null && !form.requestingArea().isBlank()) {
            despues.put("requestingArea", form.requestingArea().strip());
        }
        if (form.deadline() != null && !form.deadline().isBlank()) {
            despues.put("deadline", form.deadline().strip());
        }

        auditoria.registrar(ENTIDAD, id, "CREATE", responsable, responsable, null, despues, null)
                .ifPresent(evento -> auditoria.referenciarEstadosAdministrativos(
                        evento, form.administrativeStatusId()));

        return new Resultado(id, Map.of(), validador.advertenciaDeFechas(form).orElse(null));
    }

    /**
     * Edita un procedimiento existente.
     *
     * <p>El permiso se revalida <b>despues</b> de bloquear la fila: si entre la
     * comprobacion y la escritura alguien reasignara el procedimiento, la
     * comprobacion previa ya no valdria.
     */
    @Transactional
    public Resultado editar(UUID id, AdministrativeProcedureForm form, CuentaActual actor) {
        var fila = procedimientos.bloquearParaEditar(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("procedimiento inexistente"));

        UUID responsable = (UUID) fila.get("owner_id");
        long versionActual = ((Number) fila.get("version")).longValue();

        if (!permisos.puedeEditar(actor, responsable)) {
            throw new ErrorHandling.SinPermiso("no puede editar procedimientos ajenos");
        }

        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }
        if (form.version() == null || form.version() != versionActual) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este procedimiento");
        }

        String numero = form.fileNumber().strip();
        if (procedimientos.numeroYaUsadoPorOtro(numero, id)) {
            errores.put("fileNumber", "Ya existe otro procedimiento con ese numero.");
            return Resultado.con(errores);
        }

        var antesValores = procedimientos.porId(id).orElseThrow();
        Map<String, Object> antes = instantanea(antesValores);

        if (!procedimientos.actualizar(id, form, versionActual, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este procedimiento");
        }

        var despuesValores = procedimientos.porId(id).orElseThrow();

        // owner_id guarda el responsable de ENTONCES, no se relee despues.
        auditoria.registrar(ENTIDAD, id, "UPDATE", actor.id(), responsable,
                        antes, instantanea(despuesValores), null)
                .ifPresent(evento -> auditoria.referenciarEstadosAdministrativos(evento,
                        antesValores.administrativeStatusId(), form.administrativeStatusId()));

        return new Resultado(id, Map.of(), validador.advertenciaDeFechas(form).orElse(null));
    }

    /** Cambia la visibilidad sin tocar el estado. */
    @Transactional
    public void cambiarVisibilidad(UUID id, boolean visible, long version, CuentaActual actor) {
        var fila = procedimientos.bloquearParaEditar(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("procedimiento inexistente"));
        UUID responsable = (UUID) fila.get("owner_id");

        if (!permisos.puedeEditar(actor, responsable)) {
            throw new ErrorHandling.SinPermiso("no puede editar procedimientos ajenos");
        }
        boolean visibleAntes = procedimientos.porId(id).orElseThrow().active();
        if (visibleAntes == visible) {
            return;   // no-op: sin cambio no hay historial
        }
        if (!procedimientos.cambiarVisibilidad(id, visible, version, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este procedimiento");
        }
        auditoria.registrar(ENTIDAD, id, "VISIBILITY", actor.id(), responsable,
                Map.of("active", visibleAntes), Map.of("active", visible), null);
    }

    /** Campos que se comparan para decidir si hubo cambio y que se registra. */
    private Map<String, Object> instantanea(AdministrativeProcedure p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileNumber", p.fileNumber());
        m.put("sequenceNumber", p.sequenceNumber());
        m.put("requestingArea", p.requestingArea());
        m.put("request", p.request());
        m.put("administrativeStatusId", p.administrativeStatusId() == null ? null
                : p.administrativeStatusId().toString());
        m.put("receivedAt", p.receivedAt() == null ? null : p.receivedAt().toString());
        m.put("deadline", p.deadline() == null ? null : p.deadline().toString());
        m.put("notes", p.notes());
        return m;
    }
}
