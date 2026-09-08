package pe.org.beneficencia.legalcontrol.access;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Administracion de cuentas. Todo aqui es exclusivo de JEFA.
 *
 * <p>Las pantallas que muestran un codigo llevan {@code Cache-Control: no-store}
 * y {@code Referrer-Policy: no-referrer}: el codigo no debe quedar en el cache
 * del navegador, ni viajar en un Referer, ni recuperarse con el boton atras.
 */
@Controller
public class UserAdminController {

    private final UserAdminService cuentas;

    public UserAdminController(UserAdminService cuentas) {
        this.cuentas = cuentas;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    private CuentaActual exigirJefa(HttpSession sesion) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null || !actual.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra cuentas");
        }
        return actual;
    }

    /** Ninguna pantalla con un codigo debe quedar en cache ni recuperarse con atras. */
    private void sinRastro(HttpServletResponse respuesta) {
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        respuesta.setHeader(HttpHeaders.PRAGMA, "no-cache");
        respuesta.setHeader("Referrer-Policy", "no-referrer");
    }

    @GetMapping("/usuarios")
    public String listado(HttpSession sesion, Model modelo) {
        exigirJefa(sesion);
        modelo.addAttribute("cuentas", cuentas.listar());
        modelo.addAttribute("tituloPagina", "Cuentas del area");
        return "users/list";
    }

    @GetMapping("/usuarios/nuevo")
    public String formulario(HttpSession sesion, Model modelo) {
        exigirJefa(sesion);
        modelo.addAttribute("tituloPagina", "Nueva cuenta");
        return "users/form";
    }

    @PostMapping("/usuarios")
    public String crear(@RequestParam String name, @RequestParam String email,
                        @RequestParam String role,
                        HttpSession sesion, HttpServletResponse respuesta, Model modelo) {
        CuentaActual jefa = exigirJefa(sesion);
        var alta = cuentas.crear(name, email, role, jefa);

        if (!alta.correcta()) {
            modelo.addAttribute("error", alta.error());
            modelo.addAttribute("tituloPagina", "Nueva cuenta");
            return "users/form";
        }

        sinRastro(respuesta);
        modelo.addAttribute("codigo", alta.codigo());
        modelo.addAttribute("nombre", name);
        modelo.addAttribute("correo", email);
        modelo.addAttribute("accion", "activar la cuenta");
        modelo.addAttribute("horas", 24);
        modelo.addAttribute("tituloPagina", "Código de activación");
        return "users/codigo";
    }

    @PostMapping("/usuarios/{id}/desactivar")
    public String desactivar(@PathVariable UUID id, HttpSession sesion) {
        cuentas.desactivar(id, exigirJefa(sesion));
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/reactivar")
    public String reactivar(@PathVariable UUID id, HttpSession sesion,
                            HttpServletResponse respuesta, Model modelo) {
        CuentaActual jefa = exigirJefa(sesion);
        String codigo = cuentas.solicitarReactivacion(id, jefa);

        sinRastro(respuesta);
        modelo.addAttribute("codigo", codigo);
        modelo.addAttribute("accion", "reactivar la cuenta");
        modelo.addAttribute("horas", 24);
        modelo.addAttribute("tituloPagina", "Código de reactivación");
        return "users/codigo";
    }

    @PostMapping("/usuarios/{id}/reemitir")
    public String reemitir(@PathVariable UUID id, HttpSession sesion,
                           HttpServletResponse respuesta, Model modelo) {
        CuentaActual jefa = exigirJefa(sesion);
        String codigo = cuentas.reemitirCodigo(id, jefa);

        sinRastro(respuesta);
        modelo.addAttribute("codigo", codigo);
        modelo.addAttribute("accion", "completar el acceso");
        modelo.addAttribute("horas", 24);
        modelo.addAttribute("tituloPagina", "Código reemitido");
        return "users/codigo";
    }

    @PostMapping("/usuarios/{id}/restablecer")
    public String restablecer(@PathVariable UUID id, HttpSession sesion,
                              HttpServletResponse respuesta, Model modelo) {
        CuentaActual jefa = exigirJefa(sesion);
        String codigo = cuentas.emitirRestablecimiento(id, jefa);

        sinRastro(respuesta);
        modelo.addAttribute("codigo", codigo);
        modelo.addAttribute("accion", "establecer una contraseña nueva");
        modelo.addAttribute("horas", 1);
        modelo.addAttribute("tituloPagina", "Código de restablecimiento");
        return "users/codigo";
    }
}
