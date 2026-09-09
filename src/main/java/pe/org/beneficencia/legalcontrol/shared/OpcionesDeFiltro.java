package pe.org.beneficencia.legalcontrol.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;

/**
 * Lo que necesita una pantalla de listado para pintar sus desplegables de filtro.
 *
 * <p>Existe como componente y no escrito en cada controlador para que las tres listas
 * pidan lo mismo de la misma forma, y sobre todo para que el coste sea <b>dos
 * consultas</b>: una para todos los catalogos de la pantalla y otra para las cuentas.
 * Una por desplegable llevaria el listado de pendientes de 6 consultas a 10.
 */
@Component
public class OpcionesDeFiltro {

    private final CatalogRepository catalogos;
    private final JdbcClient jdbc;

    public OpcionesDeFiltro(CatalogRepository catalogos, JdbcClient jdbc) {
        this.catalogos = catalogos;
        this.jdbc = jdbc;
    }

    /**
     * Pone en el modelo las opciones de los catalogos indicados y las de responsable.
     *
     * <p>Las claves de los catalogos son las de {@link CatalogDefinition#clave()}, para
     * que la plantilla pida por el mismo nombre con el que se declaro.
     */
    public void poblar(Model modelo, List<CatalogDefinition> deEstaPantalla) {
        modelo.addAttribute("opcionesDeCatalogo", catalogos.habilitadosDeVarios(deEstaPantalla));
        modelo.addAttribute("opcionesDeResponsable", responsables());
    }

    /**
     * <b>Todas</b> las cuentas, tambien las desactivadas.
     *
     * <p>No se usa {@code DestinosDeAsignacion.activos}, que es la reutilizacion que
     * parece evidente y seria un error: excluye una cuenta a proposito —al reasignar no
     * tiene sentido ofrecerse a uno mismo— y solo trae las activas.
     *
     * <p>Un filtro necesita lo contrario. Si una abogada deja el puesto y su cuenta se
     * desactiva, sus expedientes y pendientes siguen existiendo: sin su nombre aqui, ese
     * trabajo deja de poder buscarse y parece de nadie. Se marcan como desactivadas en el
     * texto para que se entienda por que aparecen.
     */
    private List<Map<String, Object>> responsables() {
        List<Map<String, Object>> opciones = new ArrayList<>();
        // La cuenta no tiene una marca booleana sino un estado: ACTIVE,
        // PENDING_ACTIVATION, INACTIVE o PENDING_REACTIVATION. «Desactivada» es
        // exactamente INACTIVE; una cuenta que aun no ha entrado por primera vez no
        // esta desactivada, solo pendiente, y no hay que marcarla como si lo estuviera.
        for (Map<String, Object> fila : jdbc.sql("""
                SELECT id, name, status FROM app_user ORDER BY lower(btrim(name))
                """).query().listOfRows()) {
            boolean activa = !"INACTIVE".equals(String.valueOf(fila.get("status")));
            opciones.add(Map.of(
                    "id", fila.get("id"),
                    "name", activa ? fila.get("name") : fila.get("name") + " (desactivada)"));
        }
        return opciones;
    }
}
