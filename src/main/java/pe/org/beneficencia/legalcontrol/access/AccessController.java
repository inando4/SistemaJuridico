package pe.org.beneficencia.legalcontrol.access;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Pantalla de acceso.
 *
 * <p>No expone registro publico ni formulario de recuperacion: quien olvida su
 * contrasena le pide un codigo a la jefa. Eso elimina de raiz cualquier
 * superficie que permita averiguar si una cuenta existe.
 */
@Controller
public class AccessController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String expirada,
                        @RequestParam(required = false) String salida,
                        Model modelo) {
        if (error != null) {
            // Mensaje unico para credencial incorrecta, cuenta inexistente,
            // pendiente o desactivada.
            modelo.addAttribute("aviso", "acceso.error.credenciales");
            modelo.addAttribute("avisoEsError", true);
        } else if (expirada != null) {
            modelo.addAttribute("aviso", "acceso.error.sesion-caducada");
            modelo.addAttribute("avisoEsError", true);
        } else if (salida != null) {
            modelo.addAttribute("aviso", "acceso.salida-correcta");
            modelo.addAttribute("avisoEsError", false);
        }
        return "access/login";
    }
}
