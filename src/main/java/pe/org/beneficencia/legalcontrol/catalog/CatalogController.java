package pe.org.beneficencia.legalcontrol.catalog;

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

/**
 * Una sola pantalla para los cinco catalogos del sistema.
 *
 * <p>Consultarlos lo puede hacer cualquiera: entender por que un expediente esta en
 * cierto estado no deberia requerir permisos. Modificarlos, solo JEFA.
 *
 * <p>Las rutas se mantienen tal como estaban —{@code /estados-procesales},
 * {@code /estados-administrativos} y las tres nuevas— para no romper enlaces ni
 * costumbre de uso.
 */
@Controller
public class CatalogController {

    private final CatalogRepository catalogos;
    private final CatalogService servicio;

    public CatalogController(CatalogRepository catalogos, CatalogService servicio) {
        this.catalogos = catalogos;
        this.servicio = servicio;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    private CuentaActual exigirJefa(HttpSession sesion) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null || !actual.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra los catalogos");
        }
        return actual;
    }

    @GetMapping({"/estados-procesales", "/estados-administrativos",
                 "/tipos-de-pendiente", "/prioridades", "/estados-de-pendiente"})
    public String listado(jakarta.servlet.http.HttpServletRequest peticion, Model modelo) {
        CatalogDefinition catalogo = deLaRuta(peticion);
        modelo.addAttribute("catalogo", catalogo);
        modelo.addAttribute("valores", catalogos.todos(catalogo));
        modelo.addAttribute("tituloPagina", catalogo.titulo());
        return "catalogs/list";
    }

    @PostMapping({"/estados-procesales", "/estados-administrativos",
                  "/tipos-de-pendiente", "/prioridades", "/estados-de-pendiente"})
    public String crear(@RequestParam String name,
                        @RequestParam(required = false) String description,
                        jakarta.servlet.http.HttpServletRequest peticion,
                        HttpSession sesion, RedirectAttributes flash) {
        CatalogDefinition catalogo = deLaRuta(peticion);
        servicio.crear(catalogo, name, description, exigirJefa(sesion))
                .ifPresent(motivo -> flash.addFlashAttribute("error", motivo));
        return "redirect:/" + catalogo.clave();
    }

    @PostMapping({"/estados-procesales/{id}/disponibilidad",
                  "/estados-administrativos/{id}/disponibilidad",
                  "/tipos-de-pendiente/{id}/disponibilidad",
                  "/prioridades/{id}/disponibilidad",
                  "/estados-de-pendiente/{id}/disponibilidad"})
    public String disponibilidad(@PathVariable UUID id, @RequestParam boolean enabled,
                                 @RequestParam long version,
                                 jakarta.servlet.http.HttpServletRequest peticion,
                                 HttpSession sesion) {
        CatalogDefinition catalogo = deLaRuta(peticion);
        servicio.cambiarDisponibilidad(catalogo, id, enabled, version, exigirJefa(sesion));
        return "redirect:/" + catalogo.clave();
    }

    @PostMapping({"/estados-procesales/{id}/eliminar",
                  "/estados-administrativos/{id}/eliminar",
                  "/tipos-de-pendiente/{id}/eliminar",
                  "/prioridades/{id}/eliminar",
                  "/estados-de-pendiente/{id}/eliminar"})
    public String eliminar(@PathVariable UUID id, @RequestParam long version,
                           jakarta.servlet.http.HttpServletRequest peticion,
                           HttpSession sesion, RedirectAttributes flash) {
        CatalogDefinition catalogo = deLaRuta(peticion);
        servicio.eliminar(catalogo, id, version, exigirJefa(sesion))
                .ifPresent(motivo -> flash.addFlashAttribute("error", motivo));
        return "redirect:/" + catalogo.clave();
    }

    /** La ruta dice de que catalogo se trata: es su primer segmento. */
    private CatalogDefinition deLaRuta(jakarta.servlet.http.HttpServletRequest peticion) {
        String[] partes = peticion.getRequestURI().split("/");
        return CatalogDefinition.porClave(partes[1]);
    }
}
