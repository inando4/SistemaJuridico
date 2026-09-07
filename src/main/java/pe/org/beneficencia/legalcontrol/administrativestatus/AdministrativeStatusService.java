package pe.org.beneficencia.legalcontrol.administrativestatus;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Administracion del catalogo de estados administrativos.
 *
 * <p><b>Deshabilitar y borrar no son lo mismo.</b> Deshabilitar retira el estado
 * de las opciones nuevas y lo conserva donde ya se uso; borrar solo se permite si
 * nunca lo uso nadie. Un estado que aparece en el historial no puede desaparecer,
 * o ese historial dejaria de poder explicarse.
 */
@Service
public class AdministrativeStatusService {

    private static final String ENTIDAD = "ADMINISTRATIVE_STATUS";

    private final AdministrativeStatusRepository catalogo;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public AdministrativeStatusService(AdministrativeStatusRepository catalogo,
                                       AuditRecorder auditoria, Clock clock) {
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public Optional<String> crear(String nombre, String descripcion, CuentaActual jefa) {
        exigirJefa(jefa);
        if (nombre == null || nombre.isBlank()) {
            return Optional.of("El nombre es obligatorio.");
        }
        if (catalogo.nombreYaUsado(nombre, null)) {
            return Optional.of("Ya existe un estado con ese nombre.");
        }

        UUID id = catalogo.insertar(nombre, descripcion, jefa.id(), clock.instant());
        auditoria.registrar(ENTIDAD, id, "CREATE", jefa.id(), jefa.id(), null,
                Map.of("name", nombre.strip(), "enabled", true), null);
        return Optional.empty();
    }

    @Transactional
    public void cambiarDisponibilidad(UUID id, boolean habilitado, long version, CuentaActual jefa) {
        exigirJefa(jefa);
        var actual = catalogo.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("estado inexistente"));
        boolean antes = (boolean) actual.get("enabled");
        if (antes == habilitado) {
            return;   // no-op
        }
        if (!catalogo.cambiarDisponibilidad(id, habilitado, version, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este estado");
        }
        auditoria.registrar(ENTIDAD, id, "AVAILABILITY", jefa.id(), jefa.id(),
                Map.of("enabled", antes), Map.of("enabled", habilitado), null);
    }

    /** @return vacio si se borro; el motivo del rechazo si no */
    @Transactional
    public Optional<String> eliminar(UUID id, long version, CuentaActual jefa) {
        exigirJefa(jefa);
        var actual = catalogo.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("estado inexistente"));

        if (catalogo.enUsoActual(id)) {
            return Optional.of("Este estado esta en uso por algun procedimiento. "
                    + "Puede deshabilitarlo para que no se ofrezca en adelante.");
        }
        if (catalogo.enUsoHistorico(id)) {
            return Optional.of("Este estado aparece en el historial de algun procedimiento y no "
                    + "puede borrarse. Puede deshabilitarlo.");
        }

        auditoria.registrar(ENTIDAD, id, "DELETE", jefa.id(), jefa.id(),
                Map.of("name", actual.get("name")), null, null);

        if (!catalogo.eliminar(id, version)) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este estado");
        }
        return Optional.empty();
    }

    private void exigirJefa(CuentaActual actor) {
        if (actor == null || !actor.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra el catalogo");
        }
    }
}
