package pe.org.beneficencia.legalcontrol.pendingtask;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;

/**
 * Provee los tres catalogos habilitados a los formularios.
 *
 * <p>Solo los habilitados: uno deshabilitado deja de ofrecerse, aunque los
 * pendientes que ya lo tengan lo conserven.
 */
@Component
public class PendingTaskCatalogs {

    private final JdbcClient jdbc;
    private final CatalogRepository catalogos;

    public PendingTaskCatalogs(JdbcClient jdbc, CatalogRepository catalogos) {
        this.jdbc = jdbc;
        this.catalogos = catalogos;
    }

    public void poblar(Model modelo) {
        modelo.addAttribute("tipos",
                catalogos.habilitados(CatalogDefinition.TIPOS_DE_PENDIENTE));
        modelo.addAttribute("prioridades",
                catalogos.habilitados(CatalogDefinition.PRIORIDADES));
        modelo.addAttribute("estados",
                catalogos.habilitados(CatalogDefinition.ESTADOS_DE_PENDIENTE));
        modelo.addAttribute("expedientesJudiciales", jdbc.sql("""
                SELECT id, case_number AS name FROM judicial_case
                WHERE active = true ORDER BY lower(btrim(case_number)) LIMIT 500
                """).query().listOfRows());
        modelo.addAttribute("expedientesAdministrativos", jdbc.sql("""
                SELECT id, file_number AS name FROM administrative_procedure
                WHERE active = true ORDER BY lower(btrim(file_number)) LIMIT 500
                """).query().listOfRows());
    }

}
