package pe.org.beneficencia.legalcontrol.catalog;

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
 * Reglas comunes a los cinco catalogos.
 *
 * <p><b>Deshabilitar y borrar no son lo mismo.</b> Deshabilitar retira el valor de
 * las opciones nuevas y lo conserva donde ya se uso; borrar solo se permite si nunca
 * lo uso nadie. Un valor que aparece en el historial no puede desaparecer, o ese
 * historial dejaria de poder explicarse.
 */
@Service
public class CatalogService {

    private final CatalogRepository catalogos;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public CatalogService(CatalogRepository catalogos, AuditRecorder auditoria, Clock clock) {
        this.catalogos = catalogos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public Optional<String> crear(CatalogDefinition catalogo, String nombre, String descripcion,
                                  CuentaActual jefa) {
        exigirJefa(jefa);
        if (nombre == null || nombre.isBlank()) {
            return Optional.of("El nombre es obligatorio.");
        }
        if (catalogos.nombreYaUsado(catalogo, nombre, null)) {
            return Optional.of("Ya existe un valor con ese nombre en este catalogo.");
        }

        UUID id = catalogos.insertar(catalogo, nombre, descripcion, jefa.id(), clock.instant());
        auditoria.registrar(catalogo.entidadAuditoria(), id, "CREATE", jefa.id(), jefa.id(), null,
                Map.of("name", nombre.strip(), "enabled", true), null);
        return Optional.empty();
    }

    @Transactional
    public void cambiarDisponibilidad(CatalogDefinition catalogo, UUID id, boolean habilitado,
                                      long version, CuentaActual jefa) {
        exigirJefa(jefa);
        var actual = catalogos.porId(catalogo, id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("valor inexistente"));

        boolean antes = (boolean) actual.get("enabled");
        if (antes == habilitado) {
            return;   // no-op
        }
        if (!catalogos.cambiarDisponibilidad(catalogo, id, habilitado, version, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este valor");
        }
        auditoria.registrar(catalogo.entidadAuditoria(), id, "AVAILABILITY", jefa.id(), jefa.id(),
                Map.of("enabled", antes), Map.of("enabled", habilitado), null);
    }

    /** @return vacio si se borro; el motivo del rechazo si no */
    @Transactional
    public Optional<String> eliminar(CatalogDefinition catalogo, UUID id, long version,
                                     CuentaActual jefa) {
        exigirJefa(jefa);
        var actual = catalogos.porId(catalogo, id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("valor inexistente"));

        if (catalogos.enUsoActual(catalogo, id)) {
            return Optional.of("Este valor esta en uso. Puede deshabilitarlo para que no se "
                    + "ofrezca en adelante.");
        }
        if (catalogos.enUsoHistorico(catalogo, id)) {
            return Optional.of("Este valor aparece en el historial y no puede borrarse. "
                    + "Puede deshabilitarlo.");
        }

        auditoria.registrar(catalogo.entidadAuditoria(), id, "DELETE", jefa.id(), jefa.id(),
                Map.of("name", actual.get("name")), null, null);

        if (!catalogos.eliminar(catalogo, id, version)) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este valor");
        }
        return Optional.empty();
    }

    private void exigirJefa(CuentaActual actor) {
        if (actor == null || !actor.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra los catalogos");
        }
    }
}
