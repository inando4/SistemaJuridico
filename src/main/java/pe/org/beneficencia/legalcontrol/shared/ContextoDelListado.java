package pe.org.beneficencia.legalcontrol.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskController;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseController;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureController;

/** Contexto de navegación por petición: abrir otra pestaña no cambia el retorno. */
@ControllerAdvice(assignableTypes = {PendingTaskController.class, JudicialCaseController.class,
        AdministrativeProcedureController.class})
public class ContextoDelListado {
    @ModelAttribute
    public void contexto(HttpServletRequest request, Model modelo) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String base = ruta.startsWith("/judiciales") ? "/judiciales"
                : ruta.startsWith("/administrativos") ? "/administrativos" : "/pendientes";
        String consulta = VueltaAlListado.ruta("", request.getParameter("volver"));
        modelo.addAttribute("consultaRetorno", consulta.isEmpty() ? null : consulta);
        modelo.addAttribute("enlaceListado", VueltaAlListado.ruta(base, consulta));
    }
}
