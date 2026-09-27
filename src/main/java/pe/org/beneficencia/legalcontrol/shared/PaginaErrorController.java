package pe.org.beneficencia.legalcontrol.shared;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Recuperación común para errores del framework, sin exponer excepciones ni SQL. */
@Controller
public class PaginaErrorController implements ErrorController {
    @RequestMapping("/error")
    public String error(HttpServletRequest request, HttpServletResponse response, Model modelo) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int estado = codigo instanceof Integer valor && valor >= 400 && valor <= 599 ? valor : 500;
        response.setStatus(estado);
        String titulo = switch (estado) {
            case 400, 422 -> "Revise los datos de la solicitud";
            case 403 -> "No se pudo autorizar esta acción";
            case 404 -> "No encontramos esta página";
            case 409 -> "El registro cambió mientras lo consultaba";
            case 429 -> "Espere un momento antes de continuar";
            default -> "No pudimos completar la solicitud";
        };
        String ayuda = switch (estado) {
            case 400, 422 -> "Algún dato o filtro no es válido. Vuelva al formulario o al listado y revíselo.";
            case 403 -> "La sesión puede haber vencido o no tiene permiso. Vuelva al inicio y acceda de nuevo si hace falta.";
            case 404 -> "El enlace puede ser incorrecto. Vuelva al inicio para encontrar el registro.";
            case 409 -> "Abra la versión actual del registro antes de volver a guardar.";
            case 429 -> "Se recibieron demasiadas solicitudes. Inténtelo de nuevo dentro de unos minutos.";
            default -> "Revise el registro antes de volver a intentar la operación. Si el problema continúa, comuníquelo a la jefatura.";
        };
        modelo.addAttribute("tituloPagina", titulo);
        modelo.addAttribute("ayuda", ayuda);
        modelo.addAttribute("estado", estado);
        return "error/general";
    }
}
