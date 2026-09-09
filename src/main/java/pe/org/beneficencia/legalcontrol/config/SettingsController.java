package pe.org.beneficencia.legalcontrol.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Configuracion (insumo, seccion 36).
 *
 * <p>Es una pantalla de enlaces, y esa es toda su logica. Lo que la justifica no es
 * la seccion del insumo sino lo que se encontro al comprobarla: <b>cinco pantallas de
 * administracion no tenian ningun enlace entrante en todo el sistema</b> —los cinco
 * catalogos— y a {@code /usuarios} solo se llegaba desde sus propias subpaginas. La
 * jefa no podia añadir un tipo de pendiente ni dar de alta a nadie sin que alguien le
 * dictara una direccion.
 *
 * <p><b>No consulta nada.</b> Los enlaces son fijos; leer los catalogos para pintarlos
 * seria pagar viajes a la base por una lista que no cambia. Hay una prueba que lo
 * comprueba, porque es el tipo de cosa que se añade sin pensar.
 *
 * <p>El enlace a cuentas se oculta a quien no es jefa, y esa asimetria es real: los
 * catalogos dejan <b>leer</b> a cualquiera —solo restringen la escritura— mientras que
 * {@code UserAdminController} exige jefatura ya en el {@code GET}. Ocultarlo es
 * cortesia para no ofrecer algo que devolveria un rechazo; la autorizacion sigue donde
 * estaba, en el servidor.
 */
@Controller
public class SettingsController {

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/configuracion")
    public String configuracion(HttpSession sesion, Model modelo) {
        if (usuarioActual(sesion) == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }

        modelo.addAttribute("tituloPagina", "Configuración");
        return "settings/index";
    }
}
