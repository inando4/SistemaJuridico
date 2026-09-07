package pe.org.beneficencia.legalcontrol.pendingtask;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Provee los tres catalogos habilitados a los formularios.
 *
 * <p>Solo los habilitados: uno deshabilitado deja de ofrecerse, aunque los
 * pendientes que ya lo tengan lo conserven.
 */
@Component
public class PendingTaskCatalogs {

    private final JdbcClient jdbc;

    public PendingTaskCatalogs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void poblar(Model modelo) {
        modelo.addAttribute("tipos", habilitados("pending_task_type"));
        modelo.addAttribute("prioridades", habilitados("priority"));
        modelo.addAttribute("estados", habilitados("pending_task_status"));
        modelo.addAttribute("expedientesJudiciales", jdbc.sql("""
                SELECT id, case_number AS name FROM judicial_case
                WHERE active = true ORDER BY lower(btrim(case_number)) LIMIT 500
                """).query().listOfRows());
        modelo.addAttribute("expedientesAdministrativos", jdbc.sql("""
                SELECT id, file_number AS name FROM administrative_procedure
                WHERE active = true ORDER BY lower(btrim(file_number)) LIMIT 500
                """).query().listOfRows());
    }

    private List<Map<String, Object>> habilitados(String tabla) {
        // El nombre de tabla es constante del codigo, nunca entrada del usuario.
        return jdbc.sql("SELECT id, name FROM " + tabla
                + " WHERE enabled = true ORDER BY lower(btrim(name))").query().listOfRows();
    }
}
