package pe.org.beneficencia.legalcontrol.assignment;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * Los tres puntos de entrada de la reasignacion.
 *
 * <p>Uno por tipo de registro, pero <b>una sola</b> operacion detras: la logica, la
 * transaccion y el historial viven en {@link ReassignmentService}. Tres controladores
 * con tres copias de lo mismo terminarian divergiendo.
 */
@Controller
public class ReassignmentController {

    private final ReassignmentService reasignaciones;
    private final DestinosDeAsignacion destinos;

    public ReassignmentController(ReassignmentService reasignaciones,
                                  DestinosDeAsignacion destinos) {
        this.reasignaciones = reasignaciones;
        this.destinos = destinos;
    }

    @PostMapping("/judiciales/{id}/responsable")
    public String expedienteJudicial(@PathVariable UUID id,
                                     @RequestParam(required = false) UUID ownerId,
                                     @RequestParam long version,
                                     HttpSession sesion, RedirectAttributes flash) {
        return reasignar(Tipo.JUDICIAL, id, ownerId, version, sesion, flash,
                "/judiciales/" + id);
    }

    @PostMapping("/administrativos/{id}/responsable")
    public String procedimientoAdministrativo(@PathVariable UUID id,
                                              @RequestParam(required = false) UUID ownerId,
                                              @RequestParam long version,
                                              HttpSession sesion, RedirectAttributes flash) {
        return reasignar(Tipo.ADMINISTRATIVO, id, ownerId, version, sesion, flash,
                "/administrativos/" + id);
    }

    @PostMapping("/pendientes/{id}/responsable")
    public String pendienteSuelto(@PathVariable UUID id,
                                  @RequestParam(required = false) UUID ownerId,
                                  @RequestParam long version,
                                  HttpSession sesion, RedirectAttributes flash) {
        CuentaActual actor = (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
        var resultado = reasignaciones.reasignarPendienteSuelto(id, ownerId, version, actor);

        if (resultado.correcto()) {
            flash.addFlashAttribute("mensaje",
                    "Pendiente reasignado a " + destinos.nombreDe(ownerId) + ".");
        } else {
            flash.addFlashAttribute("error", resultado.error());
        }
        return "redirect:/pendientes/" + id;
    }

    private String reasignar(Tipo tipo, UUID id, UUID ownerId, long version,
                             HttpSession sesion, RedirectAttributes flash, String vuelta) {
        CuentaActual actor = (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
        var resultado = reasignaciones.reasignarExpediente(tipo, id, ownerId, version, actor);

        if (resultado.correcto()) {
            String nombre = destinos.nombreDe(ownerId);
            String sustantivo = tipo == Tipo.JUDICIAL ? "Expediente" : "Procedimiento";
            flash.addFlashAttribute("mensaje", sustantivo + " reasignado a " + nombre + ". "
                    + "Se traspasaron " + resultado.pendientesMovidos() + " pendientes.");
        } else {
            flash.addFlashAttribute("error", resultado.error());
        }
        return "redirect:" + vuelta;
    }
}
