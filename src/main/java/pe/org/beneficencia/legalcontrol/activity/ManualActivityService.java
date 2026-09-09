package pe.org.beneficencia.legalcontrol.activity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.config.ClockConfig;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Alta, correccion y retirada de actividades manuales.
 *
 * <p>Las tres escriben historial. Retirar <b>no borra</b>: pone {@code active = false}
 * y deja la fila, porque el historial tiene que poder explicar que hubo (principio VII).
 *
 * <p>La comprobacion de quien puede tocar una actividad se hace <b>aqui</b>, no
 * ocultando el boton en la plantilla. En la 005, {@code th:replace} se comio un
 * {@code th:if} y el formulario de reasignacion aparecio para los abogados: la
 * plantilla no es una frontera de autorizacion.
 */
@Service
public class ManualActivityService {

    /** Tipo de entidad en la evidencia. La V10 amplio el CHECK para admitirlo. */
    public static final String ENTIDAD = "MANUAL_ACTIVITY";

    private final ManualActivityRepository actividades;
    private final ManualActivityValidator validador;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public ManualActivityService(ManualActivityRepository actividades,
                                 ManualActivityValidator validador,
                                 AuditRecorder auditoria, Clock clock) {
        this.actividades = actividades;
        this.validador = validador;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public LocalDate hoy() {
        return LocalDate.ofInstant(clock.instant(), ClockConfig.ZONA);
    }

    public Map<String, String> validar(ManualActivityForm form) {
        return validador.validar(form, hoy());
    }

    /** Registra una actividad <b>a nombre de quien la escribe</b>, nunca de otro. */
    @Transactional
    public UUID crear(ManualActivityForm form, CuentaActual autor) {
        Instant ahora = clock.instant();
        UUID id = actividades.insertar(form, autor.id(), ahora);

        referenciarTipo(auditoria.registrar(ENTIDAD, id, "CREATE", autor.id(), autor.id(),
                null, valores(form), null).orElse(null), form.typeId());
        return id;
    }

    /**
     * Corrige una actividad existente.
     *
     * @throws ErrorHandling.SinPermiso      si no es su autor ni la jefa
     * @throws ErrorHandling.ConflictoDeEdicion si otra persona la modifico antes
     */
    @Transactional
    public void editar(UUID id, ManualActivityForm form, long version, CuentaActual actor) {
        ManualActivity antes = exigirPermiso(id, actor);
        Instant ahora = clock.instant();

        if (!actividades.actualizar(id, form, version, ahora)) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modificó esta actividad");
        }

        referenciarTipo(auditoria.registrar(ENTIDAD, id, "UPDATE", actor.id(), antes.ownerId(),
                valores(antes), valores(form), null).orElse(null), form.typeId());
    }

    /** Retira una actividad. La fila se conserva; solo deja de contarse. */
    @Transactional
    public void retirar(UUID id, long version, CuentaActual actor) {
        ManualActivity antes = exigirPermiso(id, actor);
        Instant ahora = clock.instant();

        if (!actividades.retirar(id, version, ahora)) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modificó esta actividad");
        }

        auditoria.registrar(ENTIDAD, id, "WITHDRAW", actor.id(), antes.ownerId(),
                valores(antes), Map.of("active", false), null);
    }

    /**
     * Solo el autor o la jefa (RF-020).
     *
     * <p>La jefa entra porque es quien responde del area y ya puede corregir cualquier
     * otro registro; un abogado no puede tocar el parte de trabajo de otro.
     */
    private ManualActivity exigirPermiso(UUID id, CuentaActual actor) {
        ManualActivity actividad = actividades.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("actividad inexistente"));

        if (!actividad.ownerId().equals(actor.id()) && !actor.esJefa()) {
            throw new ErrorHandling.SinPermiso("la actividad es de otra persona");
        }
        return actividad;
    }

    /**
     * Ata el valor de catalogo al evento, para que no se pueda borrar despues.
     *
     * <p>Sin esta llamada, {@code enUsoHistorico} deja de proteger el tipo y la unica
     * defensa que queda es {@code enUsoActual}. El metodo reutilizado interpreta el
     * primer valor como {@code PENDING_TASK_TYPE}, que es justo lo que hace falta.
     */
    private void referenciarTipo(UUID eventoId, UUID tipoId) {
        if (eventoId != null && tipoId != null) {
            auditoria.referenciarCatalogosDePendiente(eventoId, tipoId);
        }
    }

    private Map<String, Object> valores(ManualActivityForm form) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("performedOn", String.valueOf(form.performedOn()));
        v.put("description", form.description() == null ? null : form.description().strip());
        v.put("typeId", form.typeId() == null ? null : form.typeId().toString());
        v.put("otherType", form.otroTipoNormalizado());
        return v;
    }

    private Map<String, Object> valores(ManualActivity actividad) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("performedOn", String.valueOf(actividad.performedOn()));
        v.put("description", actividad.description());
        v.put("typeId", actividad.pendingTaskTypeId() == null
                ? null : actividad.pendingTaskTypeId().toString());
        v.put("otherType", actividad.otherType());
        return v;
    }
}
