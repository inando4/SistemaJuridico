package pe.org.beneficencia.legalcontrol.access;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Pantalla de acceso.
 *
 * <p>No expone registro publico ni formulario de recuperacion: quien olvida su
 * contrasena le pide un codigo a la jefa. Eso elimina de raiz cualquier
 * superficie que permita averiguar si una cuenta existe.
 */
@Controller
public class AccessController {

    private final RedeemService canje;
    private final SelfPasswordService propia;

    public AccessController(RedeemService canje, SelfPasswordService propia) {
        this.canje = canje;
        this.propia = propia;
    }

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

    @GetMapping("/acceso/canjear")
    public String formularioCanje(HttpServletResponse respuesta, Model modelo) {
        // El codigo se teclea aqui: la pantalla no debe quedar en cache.
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        respuesta.setHeader("Referrer-Policy", "no-referrer");
        modelo.addAttribute("tituloPagina", "Activar acceso");
        return "access/redeem";
    }

    @PostMapping("/acceso/canjear")
    public String canjear(@RequestParam String email, @RequestParam String code,
                          @RequestParam String password,
                          @RequestParam String passwordConfirmation,
                          jakarta.servlet.http.HttpServletRequest peticion,
                          HttpServletResponse respuesta, Model modelo) {
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");

        var problema = canje.canjear(email, code, password, passwordConfirmation,
                peticion.getRemoteAddr());

        if (problema.isPresent()) {
            modelo.addAttribute("error", problema.get());
            modelo.addAttribute("correo", email);   // el codigo NO se devuelve
            modelo.addAttribute("tituloPagina", "Activar acceso");
            return "access/redeem";
        }
        return "redirect:/login?activada";
    }

    @GetMapping("/cuenta/contrasena")
    public String formularioPropio(Model modelo) {
        modelo.addAttribute("tituloPagina", "Cambiar mi contrasena");
        return "access/password";
    }

    @PostMapping("/cuenta/contrasena")
    public String cambiarPropia(@RequestParam String currentPassword,
                                @RequestParam String password,
                                @RequestParam String passwordConfirmation,
                                HttpSession sesion, Model modelo) {
        CuentaActual actual = (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
        if (actual == null) {
            return "redirect:/login";
        }
        var problema = propia.cambiar(actual, currentPassword, password, passwordConfirmation);
        if (problema.isPresent()) {
            modelo.addAttribute("error", problema.get());
            modelo.addAttribute("tituloPagina", "Cambiar mi contrasena");
            return "access/password";
        }
        // La propia sesion tambien muere: hay que entrar con la contrasena nueva.
        sesion.invalidate();
        return "redirect:/login?cambiada";
    }
}
