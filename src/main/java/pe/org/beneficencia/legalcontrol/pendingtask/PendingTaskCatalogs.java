package pe.org.beneficencia.legalcontrol.pendingtask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        poblar(modelo, null);
    }

    /**
     * Igual, pero garantizando que el expediente ya elegido este entre las opciones.
     *
     * <p>Los desplegables se llenan con {@code active = true} y {@code LIMIT 500}. Un
     * expediente archivado —cuya ficha se abre con normalidad, porque {@code porId} no
     * filtra— o el 501.º no estarian ahi: {@code th:selected} no encajaria con nada, el
     * {@code <select>} enviaria vacio y <b>el vinculo se perderia al guardar sin dar
     * ningun error</b>.
     *
     * <p>Se añade la opcion que falta en lugar de fijar el valor con un campo oculto:
     * el vinculo tiene que poder cambiarse o quitarse (RF-013), y dos campos con el
     * mismo nombre enlazan de forma impredecible.
     *
     * <p>Esto tapa de paso un fallo que ya existia: {@code formularioEdicion} usa este
     * mismo metodo, asi que editar un pendiente cuyo expediente se archivo despues
     * perdia el vinculo.
     */
    public void poblar(Model modelo, ExpedienteVinculado yaElegido) {
        modelo.addAttribute("tipos",
                catalogos.habilitados(CatalogDefinition.TIPOS_DE_PENDIENTE));
        modelo.addAttribute("prioridades",
                catalogos.habilitados(CatalogDefinition.PRIORIDADES));
        modelo.addAttribute("estados",
                catalogos.habilitados(CatalogDefinition.ESTADOS_DE_PENDIENTE));
        List<Map<String, Object>> judiciales = new ArrayList<>(jdbc.sql("""
                SELECT id, case_number AS name FROM judicial_case
                WHERE active = true ORDER BY lower(btrim(case_number)) LIMIT 500
                """).query().listOfRows());
        List<Map<String, Object>> administrativos = new ArrayList<>(jdbc.sql("""
                SELECT id, file_number AS name FROM administrative_procedure
                WHERE active = true ORDER BY lower(btrim(file_number)) LIMIT 500
                """).query().listOfRows());

        modelo.addAttribute("expedientesJudiciales",
                conElYaElegido(judiciales, yaElegido, ExpedienteVinculado.Clase.JUDICIAL));
        modelo.addAttribute("expedientesAdministrativos",
                conElYaElegido(administrativos, yaElegido,
                        ExpedienteVinculado.Clase.ADMINISTRATIVO));
    }

    /**
     * Añade el expediente ya elegido si la consulta no lo trajo.
     *
     * <p>Solo a la lista de su clase: un expediente judicial ofrecido entre los
     * procedimientos administrativos seria un vinculo que la validacion rechazaria
     * despues, sin que el usuario entendiera por que.
     */
    private List<Map<String, Object>> conElYaElegido(List<Map<String, Object>> opciones,
                                                     ExpedienteVinculado elegido,
                                                     ExpedienteVinculado.Clase deEstaLista) {
        if (elegido == null || elegido.clase() != deEstaLista) {
            return opciones;
        }
        for (Map<String, Object> fila : opciones) {
            if (elegido.id().equals(fila.get("id"))) {
                return opciones;
            }
        }
        // No estaba: archivado, o mas alla del corte de 500. Se marca como archivado
        // en el texto para que quien lo ve sepa por que no lo encontraba antes.
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("id", elegido.id());
        extra.put("name", elegido.numero() + (elegido.activo() ? "" : " (archivado)"));
        List<Map<String, Object>> ampliada = new ArrayList<>(opciones);
        ampliada.add(extra);
        return ampliada;
    }

}
