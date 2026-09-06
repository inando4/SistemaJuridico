package pe.org.beneficencia.legalcontrol.proceduralstatus;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/** Catalogo de estados procesales: lo consulta cualquiera, lo administra JEFA. */
@Controller
public class ProceduralStatusController {

    private final ProceduralStatusRepository catalogo;
    private final ProceduralStatusService servicio;

    public ProceduralStatusController(ProceduralStatusRepository catalogo,
                                      ProceduralStatusService servicio) {
        this.catalogo = catalogo;
        this.servicio = servicio;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    private CuentaActual exigirJefa(HttpSession sesion) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null || !actual.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra el catalogo");
        }
        return actual;
    }

    @GetMapping("/procedural-statuses")
    public String listado(Model modelo) {
        modelo.addAttribute("estados", catalogo.todos());
        modelo.addAttribute("tituloPagina", "Estados procesales");
        return "procedural-statuses/list";
    }

    @PostMapping("/procedural-statuses")
    public String crear(@RequestParam String name,
                        @RequestParam(required = false) String description,
                        HttpSession sesion, RedirectAttributes flash) {
        servicio.crear(name, description, exigirJefa(sesion))
                .ifPresent(motivo -> flash.addFlashAttribute("error", motivo));
        return "redirect:/procedural-statuses";
    }

    @PostMapping("/procedural-statuses/{id}/availability")
    public String disponibilidad(@PathVariable UUID id, @RequestParam boolean enabled,
                                 @RequestParam long version, HttpSession sesion) {
        servicio.cambiarDisponibilidad(id, enabled, version, exigirJefa(sesion));
        return "redirect:/procedural-statuses";
    }

    @PostMapping("/procedural-statuses/{id}/delete")
    public String eliminar(@PathVariable UUID id, @RequestParam long version,
                           HttpSession sesion, RedirectAttributes flash) {
        servicio.eliminar(id, version, exigirJefa(sesion))
                .ifPresent(motivo -> flash.addFlashAttribute("error", motivo));
        return "redirect:/procedural-statuses";
    }
}
