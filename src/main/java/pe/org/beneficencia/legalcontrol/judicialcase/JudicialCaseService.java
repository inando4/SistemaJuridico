package pe.org.beneficencia.legalcontrol.judicialcase;

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
 * Alta de expedientes.
 *
 * <p><b>El responsable lo fija el servidor</b> con el usuario que crea el
 * registro. La asignacion entre abogados es funcionalidad posterior, asi que un
 * intento de asignar a otra persona se rechaza en vez de ignorarse: si alguien
 * lo pidio, tiene que enterarse de que no ocurrio.
 *
 * <p>El alta y su evidencia van en la misma transaccion. Si la auditoria falla,
 * el expediente no se crea.
 */
@Service
public class JudicialCaseService {

    private final JudicialCaseRepository expedientes;
    private final JudicialCaseValidator validador;
    private final CaseAuthorization permisos;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public JudicialCaseService(JudicialCaseRepository expedientes, JudicialCaseValidator validador,
                               CaseAuthorization permisos, AuditRecorder auditoria, Clock clock) {
        this.expedientes = expedientes;
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
    public Resultado crear(JudicialCaseForm form, UUID responsable) {
        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }

        String numero = form.caseNumber().strip();
        if (expedientes.numeroYaUsado(numero)) {
            errores.put("caseNumber",
                    "Ya existe un expediente con ese numero, aunque sea de otra persona "
                    + "o este oculto del listado.");
            return Resultado.con(errores);
        }

        UUID id;
        try {
            id = expedientes.insertar(form, responsable, clock.instant());
        } catch (DuplicateKeyException carrera) {
            // Dos altas simultaneas con el mismo numero: la restriccion unica de la
            // base decide, y aqui se traduce a un error del formulario.
            errores.put("caseNumber", "Ya existe un expediente con ese numero.");
            return Resultado.con(errores);
        }

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("caseNumber", numero);
        despues.put("ownerId", responsable.toString());
        if (form.subject() != null && !form.subject().isBlank()) {
            despues.put("subject", form.subject().strip());
        }
        if (form.deadline() != null && !form.deadline().isBlank()) {
            despues.put("deadline", form.deadline().strip());
        }

        // Alta: el «antes» es ausencia, no un mapa vacio.
        auditoria.registrar("JUDICIAL_CASE", id, "CREATE", responsable, responsable,
                null, despues, null);

        return new Resultado(id, Map.of());
    }

    /**
     * Edita un expediente existente.
     *
     * <p>El permiso se revalida <b>despues</b> de bloquear la fila, no antes: si
     * entre la comprobacion y la escritura alguien reasignara el expediente, la
     * comprobacion previa ya no valdria.
     *
     * <p>La version del formulario tiene que coincidir con la actual. Si no,
     * alguien modifico el expediente mientras este formulario estaba abierto, y
     * guardar lo pisaria en silencio: se responde 409 y se pide revisar.
     */
    @Transactional
    public Resultado editar(UUID id, JudicialCaseForm form, CuentaActual actor) {
        var fila = expedientes.bloquearParaEditar(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));

        UUID responsable = (UUID) fila.get("owner_id");
        long versionActual = ((Number) fila.get("version")).longValue();

        if (!permisos.puedeEditar(actor, responsable)) {
            throw new ErrorHandling.SinPermiso("no puede editar expedientes ajenos");
        }

        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }
        if (form.version() == null || form.version() != versionActual) {
            throw new ErrorHandling.ConflictoDeEdicion(
                    "otra persona modifico este expediente");
        }

        String numero = form.caseNumber().strip();
        if (expedientes.numeroYaUsadoPorOtro(numero, id)) {
            errores.put("caseNumber", "Ya existe otro expediente con ese numero.");
            return Resultado.con(errores);
        }

        var antesValores = expedientes.porId(id).orElseThrow();
        Map<String, Object> antes = instantanea(antesValores);

        if (!expedientes.actualizar(id, form, versionActual, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este expediente");
        }

        Map<String, Object> despues = instantanea(expedientes.porId(id).orElseThrow());

        // owner_id guarda el responsable de ENTONCES, no se relee despues: la
        // evidencia debe reflejar de quien era el expediente cuando se toco.
        auditoria.registrar("JUDICIAL_CASE", id, "UPDATE", actor.id(), responsable,
                antes, despues, null);

        return new Resultado(id, Map.of());
    }

    /** Cambia la visibilidad sin tocar el estado procesal. */
    @Transactional
    public void cambiarVisibilidad(UUID id, boolean visible, long version, CuentaActual actor) {
        var fila = expedientes.bloquearParaEditar(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));
        UUID responsable = (UUID) fila.get("owner_id");

        if (!permisos.puedeEditar(actor, responsable)) {
            throw new ErrorHandling.SinPermiso("no puede editar expedientes ajenos");
        }
        boolean visibleAntes = expedientes.porId(id).orElseThrow().active();
        if (visibleAntes == visible) {
            return;   // no-op: sin cambio no hay historial
        }
        if (!expedientes.cambiarVisibilidad(id, visible, version, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este expediente");
        }
        auditoria.registrar("JUDICIAL_CASE", id, "VISIBILITY", actor.id(), responsable,
                Map.of("active", visibleAntes), Map.of("active", visible), null);
    }

    /** Campos que se comparan para decidir si hubo cambio y que se registra. */
    private Map<String, Object> instantanea(JudicialCase c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseNumber", c.caseNumber());
        m.put("sequenceNumber", c.sequenceNumber());
        m.put("claimant", c.claimant());
        m.put("respondent", c.respondent());
        m.put("subject", c.subject());
        m.put("proceduralStatusId", c.proceduralStatusId() == null ? null
                : c.proceduralStatusId().toString());
        m.put("lastProceduralAction", c.lastProceduralAction());
        m.put("nextProceduralAction", c.nextProceduralAction());
        m.put("lastActionDate", c.lastActionDate() == null ? null : c.lastActionDate().toString());
        m.put("deadline", c.deadline() == null ? null : c.deadline().toString());
        m.put("amount", c.amount() == null ? null : c.amount().toPlainString());
        m.put("propertyAddress", c.propertyAddress());
        m.put("notes", c.notes());
        m.put("managementActions", c.managementActions());
        return m;
    }
}
