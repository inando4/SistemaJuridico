package pe.org.beneficencia.legalcontrol.assignment;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder.CambioDeResponsable;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentRepository.Vinculo;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Cambio de responsable (insumo, seccion 5.3).
 *
 * <p><b>O cambia todo, o no cambia nada.</b> El expediente y sus pendientes se
 * mueven en una sola transaccion. Una reasignacion a medias dejaria pendientes cuyo
 * responsable ya no tiene el expediente, y con ello permiso de escritura en manos
 * equivocadas: {@code PendingTaskAuthorization} decide a partir de {@code owner_id}.
 *
 * <p><b>El permiso se comprueba aqui, no en la plantilla.</b> Ocultar el formulario
 * no es autorizacion (principio II): la comprobacion tiene que sobrevivir a que
 * alguien envie la peticion a mano.
 */
@Service
public class ReassignmentService {

    /** Acciones nuevas. {@code audit_event.action} es texto libre: no hay migracion. */
    public static final String REASIGNAR = "REASSIGN";

    private final ReassignmentRepository repo;
    private final DestinosDeAsignacion destinos;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public ReassignmentService(ReassignmentRepository repo, DestinosDeAsignacion destinos,
                               AuditRecorder auditoria, Clock clock) {
        this.repo = repo;
        this.destinos = destinos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Qué tipo de registro se reasigna, con lo que cambia de un caso a otro. */
    public enum Tipo {
        JUDICIAL("judicial_case", "JUDICIAL_CASE", Vinculo.JUDICIAL, "expediente"),
        ADMINISTRATIVO("administrative_procedure", "ADMINISTRATIVE_PROCEDURE",
                Vinculo.ADMINISTRATIVO, "procedimiento");

        public final String tabla;
        public final String entidad;
        public final Vinculo vinculo;
        public final String comoSeLlama;

        Tipo(String tabla, String entidad, Vinculo vinculo, String comoSeLlama) {
            this.tabla = tabla;
            this.entidad = entidad;
            this.vinculo = vinculo;
            this.comoSeLlama = comoSeLlama;
        }
    }

    /** @param pendientesMovidos cuantos pendientes cambiaron de manos con el expediente */
    public record Resultado(String error, int pendientesMovidos) {
        public boolean correcto() {
            return error == null;
        }

        static Resultado con(String error) {
            return new Resultado(error, 0);
        }
    }

    /**
     * Reasigna un expediente y <b>todos</b> sus pendientes, cumplidos incluidos.
     *
     * <p>Los pendientes que hoy son de un tercero tambien viajan: el expediente va
     * completo. Quien ejecuta la operacion lo ha visto antes en el aviso previo.
     */
    @Transactional
    public Resultado reasignarExpediente(Tipo tipo, UUID id, UUID nuevo, long version,
                                         CuentaActual actor) {
        exigirJefatura(actor);

        var estado = repo.estadoDelExpediente(tipo.tabla, id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado(tipo.comoSeLlama + " inexistente"));

        Optional<String> reparo = validarDestino(nuevo);
        if (reparo.isPresent()) {
            return Resultado.con(reparo.get());
        }

        // La foto previa, antes de tocar nada: cada pendiente con SU responsable.
        List<CambioDeResponsable> pendientes =
                repo.pendientesQueCambiarian(tipo.vinculo, id, nuevo);

        boolean expedienteCambia = !nuevo.equals(estado.responsable());
        if (!expedienteCambia && pendientes.isEmpty()) {
            // Nada que hacer: sin cambios no se escribe historial (principio VII).
            return Resultado.con("El " + tipo.comoSeLlama
                    + " ya está a nombre de esa persona.");
        }

        if (expedienteCambia) {
            if (!repo.moverExpediente(tipo.tabla, id, nuevo, version, clock.instant())) {
                throw new ErrorHandling.ConflictoDeEdicion(
                        "otra persona modificó este " + tipo.comoSeLlama);
            }
            auditoria.registrar(tipo.entidad, id, REASIGNAR, actor.id(), estado.responsable(),
                    Map.of("ownerId", estado.responsable().toString()),
                    Map.of("ownerId", nuevo.toString()), null);
        }

        int movidos = repo.moverPendientes(tipo.vinculo, id, nuevo, clock.instant());
        auditoria.registrarEnBloque("PENDING_TASK", REASIGNAR, actor.id(), pendientes, nuevo,
                "Traspaso al reasignar el " + tipo.comoSeLlama);
        return new Resultado(null, movidos);
    }

    /**
     * Reasigna un pendiente que no cuelga de ningun expediente.
     *
     * <p>Sin esto, los pendientes sueltos de quien deja el area quedarian
     * inmovilizados: la seccion 5.3 solo describe mover expedientes.
     */
    @Transactional
    public Resultado reasignarPendienteSuelto(UUID id, UUID nuevo, long version,
                                              CuentaActual actor) {
        exigirJefatura(actor);

        var estado = repo.estadoDelPendiente(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("pendiente inexistente"));

        if (estado.estaVinculado()) {
            return Resultado.con("Este pendiente pertenece a un expediente. "
                    + "Se reasigna reasignando el expediente.");
        }

        Optional<String> reparo = validarDestino(nuevo);
        if (reparo.isPresent()) {
            return Resultado.con(reparo.get());
        }
        if (nuevo.equals(estado.responsable())) {
            return Resultado.con("El pendiente ya está a nombre de esa persona.");
        }

        if (!repo.moverPendienteSuelto(id, nuevo, version, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modificó este pendiente");
        }
        auditoria.registrar("PENDING_TASK", id, REASIGNAR, actor.id(), estado.responsable(),
                Map.of("ownerId", estado.responsable().toString()),
                Map.of("ownerId", nuevo.toString()), null);
        return new Resultado(null, 1);
    }

    private void exigirJefatura(CuentaActual actor) {
        if (actor == null || !actor.esJefa()) {
            throw new ErrorHandling.SinPermiso(
                    "solo la jefatura puede reasignar expedientes");
        }
    }

    private Optional<String> validarDestino(UUID nuevo) {
        if (nuevo == null) {
            return Optional.of("Elija a quién se reasigna.");
        }
        if (!destinos.puedeRecibir(nuevo)) {
            return Optional.of("Esa cuenta está inactiva y no puede recibir trabajo.");
        }
        return Optional.empty();
    }
}
