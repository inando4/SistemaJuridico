package pe.org.beneficencia.legalcontrol.search;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureRepository;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseRepository;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskRepository;
import pe.org.beneficencia.legalcontrol.shared.BusquedaDeTexto;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Las tres consultas del buscador global.
 *
 * <p><b>Las condiciones de texto no se escriben aqui.</b> Se toman de
 * {@code CONDICION_TEXTO} de cada repositorio, que es la misma que usa su listado.
 * Copiarlas seria garantizar que algun dia divergieran y que la misma palabra
 * devolviera cosas distintas segun donde se escriba (RF-012, CE-003).
 *
 * <p><b>Ningun {@code count(*)}.</b> El numero de consultas tiene que ser tres pase
 * lo que pase con el resultado; contar coincidencias por grupo lo haria seis.
 */
@Repository
public class GlobalSearchRepository {

    private final JdbcClient jdbc;

    public GlobalSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Expedientes judiciales que coinciden.
     *
     * <p>Sin filtro de {@code active}: el buscador alcanza los archivados y la vista
     * los señala. Es la diferencia deliberada con {@code /judiciales}, que por omision
     * muestra solo los activos.
     */
    public List<ResultadoDeBusqueda> judiciales(String termino, Paging pagina) {
        return jdbc.sql("""
                SELECT c.id,
                       c.case_number AS titulo,
                       btrim(coalesce(c.claimant, '')
                             || CASE WHEN c.claimant IS NOT NULL AND c.respondent IS NOT NULL
                                     THEN ' c. ' ELSE '' END
                             || coalesce(c.respondent, '')) AS descripcion,
                       u.name AS responsable,
                       c.active AS activo
                FROM judicial_case c
                JOIN app_user u ON u.id = c.owner_id
                WHERE """ + JudicialCaseRepository.CONDICION_TEXTO + """

                ORDER BY c.active DESC, c.updated_at DESC, c.id ASC
                LIMIT :limite OFFSET :salto
                """)
                .param("q", BusquedaDeTexto.comodin(termino))
                .param("limite", pagina.limitConSondeo()).param("salto", pagina.offset())
                .query(ResultadoDeBusqueda.class).list();
    }

    /** Procedimientos administrativos que coinciden. */
    public List<ResultadoDeBusqueda> administrativos(String termino, Paging pagina) {
        return jdbc.sql("""
                SELECT p.id,
                       p.file_number AS titulo,
                       btrim(coalesce(p.requesting_area, '')
                             || CASE WHEN p.requesting_area IS NOT NULL AND p.request IS NOT NULL
                                     THEN ' — ' ELSE '' END
                             || coalesce(p.request, '')) AS descripcion,
                       u.name AS responsable,
                       p.active AS activo
                FROM administrative_procedure p
                JOIN app_user u ON u.id = p.owner_id
                WHERE """ + AdministrativeProcedureRepository.CONDICION_TEXTO + """

                ORDER BY p.active DESC, p.updated_at DESC, p.id ASC
                LIMIT :limite OFFSET :salto
                """)
                .param("q", BusquedaDeTexto.comodin(termino))
                .param("limite", pagina.limitConSondeo()).param("salto", pagina.offset())
                .query(ResultadoDeBusqueda.class).list();
    }

    /** Pendientes que coinciden, cumplidos incluidos. */
    public List<ResultadoDeBusqueda> pendientes(String termino, Paging pagina) {
        return jdbc.sql("""
                SELECT t.id,
                       t.title AS titulo,
                       coalesce(t.description, '') AS descripcion,
                       u.name AS responsable,
                       t.active AS activo
                FROM pending_task t
                JOIN app_user u ON u.id = t.owner_id
                WHERE """ + PendingTaskRepository.CONDICION_TEXTO + """

                ORDER BY t.active DESC, t.updated_at DESC, t.id ASC
                LIMIT :limite OFFSET :salto
                """)
                .param("q", BusquedaDeTexto.comodin(termino))
                .param("limite", pagina.limitConSondeo()).param("salto", pagina.offset())
                .query(ResultadoDeBusqueda.class).list();
    }
}
