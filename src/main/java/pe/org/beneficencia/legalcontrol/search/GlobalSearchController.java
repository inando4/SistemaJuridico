package pe.org.beneficencia.legalcontrol.search;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.shared.BusquedaDeTexto;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Buscador global (insumo, seccion 34).
 *
 * <p><b>La lectura es de todos</b> (seccion 5.1): el buscador devuelve registros de
 * toda el area, sea quien sea su responsable.
 *
 * <p>Los nombres de parametro son los del contrato y estan en ingles como el resto
 * del sistema. En la 005 se inventaron equivalentes en español que no existian, los
 * enlaces dejaron de filtrar y el listado enseño los pendientes de todo el mundo.
 */
@Controller
public class GlobalSearchController {

    private final GlobalSearchRepository busqueda;

    public GlobalSearchController(GlobalSearchRepository busqueda) {
        this.busqueda = busqueda;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/buscar")
    public String buscar(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int pageJ,
                         @RequestParam(defaultValue = "0") int pageA,
                         @RequestParam(defaultValue = "0") int pageP,
                         HttpSession sesion, Model modelo) {
        if (usuarioActual(sesion) == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }

        modelo.addAttribute("tituloPagina", "Buscar");
        modelo.addAttribute("q", q);
        modelo.addAttribute("minimo", BusquedaDeTexto.MINIMO);

        // Sin termino no hay pantalla de resultados vacia: hay pantalla de buscador.
        // Son dos cosas distintas y confundirlas hace pensar que no se encontro nada.
        if (q == null || q.isBlank()) {
            modelo.addAttribute("estado", "sin-termino");
            return "search/results";
        }

        // El corte va ANTES de consultar, no filtrando despues: con «a» el comodin
        // coincide con casi todo y serian tres recorridos completos para nada.
        if (!BusquedaDeTexto.suficiente(q)) {
            modelo.addAttribute("estado", "termino-corto");
            return "search/results";
        }

        String termino = q.strip();
        Paging paginaJ = Paging.of(pageJ);
        Paging paginaA = Paging.of(pageA);
        Paging paginaP = Paging.of(pageP);

        var judiciales = GrupoDeResultados.desdeSondeo(
                busqueda.judiciales(termino, paginaJ), paginaJ);
        var administrativos = GrupoDeResultados.desdeSondeo(
                busqueda.administrativos(termino, paginaA), paginaA);
        var pendientes = GrupoDeResultados.desdeSondeo(
                busqueda.pendientes(termino, paginaP), paginaP);

        modelo.addAttribute("judiciales", judiciales);
        modelo.addAttribute("administrativos", administrativos);
        modelo.addAttribute("pendientes", pendientes);
        modelo.addAttribute("paginaJ", paginaJ.page());
        modelo.addAttribute("paginaA", paginaA.page());
        modelo.addAttribute("paginaP", paginaP.page());
        modelo.addAttribute("estado",
                judiciales.vacio() && administrativos.vacio() && pendientes.vacio()
                        ? "sin-resultados" : "con-resultados");
        return "search/results";
    }
}
