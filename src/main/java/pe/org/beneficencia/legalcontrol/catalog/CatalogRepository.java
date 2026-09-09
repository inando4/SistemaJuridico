package pe.org.beneficencia.legalcontrol.catalog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso comun a los cinco catalogos del sistema.
 *
 * <p>Todos comparten columnas y operaciones; lo que cambia es la tabla, que llega en
 * la {@link CatalogDefinition}. Sus nombres son constantes del codigo, nunca entrada
 * del usuario, asi que componerlos en el SQL no abre ninguna puerta.
 */
@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> todos(CatalogDefinition catalogo) {
        return jdbc.sql("SELECT id, name, description, enabled, version FROM " + catalogo.tabla()
                + " ORDER BY lower(btrim(name))").query().listOfRows();
    }

    /** Solo los habilitados: son los unicos que se ofrecen para elegir de nuevo. */
    /**
     * El criterio de «opcion ofrecible»: solo las habilitadas.
     *
     * <p>Va aparte del orden porque una rama de {@code UNION ALL} admite el filtro
     * pero no su propio {@code ORDER BY}. Y va como constante, y no escrito en cada
     * consulta, porque lo usan dos caminos: {@link #habilitados} y
     * {@link #habilitadosDeVarios}. Escrito dos veces, uno de los dos se quedaria sin
     * la siguiente correccion —es lo que hubo que arreglar en la 004— y un
     * desplegable ofreceria opciones que otro no, sin que nada avisara.
     */
    private static final String SOLO_HABILITADOS = " WHERE enabled = true";

    /** Por nombre, sin distinguir mayusculas ni espacios sobrantes. */
    private static final String POR_NOMBRE = "lower(btrim(name))";

    public List<Map<String, Object>> habilitados(CatalogDefinition catalogo) {
        return jdbc.sql("SELECT id, name FROM " + catalogo.tabla() + SOLO_HABILITADOS
                        + " ORDER BY " + POR_NOMBRE)
                .query().listOfRows();
    }

    /**
     * Las opciones de varios catalogos en <b>una sola consulta</b>.
     *
     * <p>Una pantalla de listado necesita tantos desplegables como catalogos filtre.
     * Con una consulta cada uno, el listado de pendientes pasaria de las 6 consultas
     * que cuesta hoy a 10 —un 67 % mas en la pantalla mas usada del sistema— para
     * pintar unas listas de menos de veinte filas. La forma de {@code UNION ALL} es
     * la misma que resuelve el calendario en una consulta desde la 006.
     *
     * <p>Los nombres de tabla salen de las constantes de {@link CatalogDefinition},
     * nunca de entrada del usuario: no hay superficie de inyeccion al componerlos.
     *
     * @return las filas agrupadas por la clave del catalogo. Un catalogo sin opciones
     *         habilitadas devuelve lista vacia, no falta de la respuesta
     */
    public Map<String, List<Map<String, Object>>> habilitadosDeVarios(
            List<CatalogDefinition> catalogos) {
        Map<String, List<Map<String, Object>>> porCatalogo = new LinkedHashMap<>();
        for (CatalogDefinition c : catalogos) {
            porCatalogo.put(c.clave(), new ArrayList<>());
        }
        if (catalogos.isEmpty()) {
            return porCatalogo;
        }

        StringBuilder ramas = new StringBuilder();
        for (CatalogDefinition c : catalogos) {
            if (!ramas.isEmpty()) {
                ramas.append(" UNION ALL ");
            }
            ramas.append("SELECT '").append(c.clave()).append("' AS catalogo, id, name FROM ")
                 .append(c.tabla()).append(SOLO_HABILITADOS);
        }

        // La union va envuelta en una subconsulta, y no ordenada directamente, porque
        // PostgreSQL solo admite nombres de columna —no expresiones— en el ORDER BY de
        // un UNION. Y el orden hace falta: sin el, el motor no garantiza ninguno para
        // la union y los desplegables saldrian revueltos.
        StringBuilder sql = new StringBuilder("SELECT catalogo, id, name FROM (")
                .append(ramas)
                .append(") AS opciones ORDER BY catalogo, ").append(POR_NOMBRE);

        for (Map<String, Object> fila : jdbc.sql(sql.toString()).query().listOfRows()) {
            List<Map<String, Object>> destino =
                    porCatalogo.get(String.valueOf(fila.get("catalogo")));
            if (destino != null) {
                Map<String, Object> opcion = new LinkedHashMap<>();
                opcion.put("id", fila.get("id"));
                opcion.put("name", fila.get("name"));
                destino.add(opcion);
            }
        }
        return porCatalogo;
    }

    public Optional<Map<String, Object>> porId(CatalogDefinition catalogo, UUID id) {
        return jdbc.sql("SELECT id, name, description, enabled, version FROM " + catalogo.tabla()
                        + " WHERE id = :id")
                .param("id", id).query().listOfRows().stream().findFirst();
    }

    public UUID insertar(CatalogDefinition catalogo, String nombre, String descripcion,
                         UUID actor, Instant ahora) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO " + catalogo.tabla()
                        + " (id, name, description, enabled, created_by, created_at,"
                        + " updated_at, version)"
                        + " VALUES (:id, :nombre, :desc, true, :actor, :ahora, :ahora, 1)")
                .param("id", id).param("nombre", nombre.strip())
                .param("desc", descripcion == null || descripcion.isBlank()
                        ? null : descripcion.strip())
                .param("actor", actor).param("ahora", Timestamp.from(ahora)).update();
        return id;
    }

    public boolean cambiarDisponibilidad(CatalogDefinition catalogo, UUID id, boolean habilitado,
                                         long version, Instant ahora) {
        return jdbc.sql("UPDATE " + catalogo.tabla()
                        + " SET enabled = :habilitado, updated_at = :ahora, version = version + 1"
                        + " WHERE id = :id AND version = :version")
                .param("id", id).param("habilitado", habilitado).param("version", version)
                .param("ahora", Timestamp.from(ahora)).update() == 1;
    }

    public boolean eliminar(CatalogDefinition catalogo, UUID id, long version) {
        return jdbc.sql("DELETE FROM " + catalogo.tabla()
                        + " WHERE id = :id AND version = :version")
                .param("id", id).param("version", version).update() == 1;
    }

    public boolean nombreYaUsado(CatalogDefinition catalogo, String nombre, UUID excepto) {
        Integer total = jdbc.sql("SELECT count(*) FROM " + catalogo.tabla()
                        + " WHERE lower(btrim(name)) = lower(btrim(:nombre))"
                        + " AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid))")
                .param("nombre", nombre).param("excepto", excepto).query(Integer.class).single();
        return total != null && total > 0;
    }

    /**
     * ¿Lo usa algun registro ahora mismo, en cualquiera de las tablas que lo referencian?
     *
     * <p>Recorre la lista y para en la primera que lo encuentre. Antes de la 006 miraba
     * una sola tabla, porque ningun catalogo tenia dos usuarios; los tipos de pendiente
     * ahora los usa tambien {@code manual_activity}, y un tipo usado solo alli se
     * declaraba «no en uso». El borrado seguia sin ocurrir —lo frenaba la comprobacion
     * de uso historico—, pero con el mensaje equivocado, y solo mientras nadie olvidara
     * escribir la referencia de catalogo al auditar. Si se olvidaba, la clave foranea
     * lanzaba una traza a la cara de la jefa.
     */
    public boolean enUsoActual(CatalogDefinition catalogo, UUID id) {
        for (CatalogDefinition.UsoDeCatalogo uso : catalogo.usos()) {
            Integer total = jdbc.sql("SELECT count(*) FROM " + uso.tabla()
                            + " WHERE " + uso.columna() + " = :id")
                    .param("id", id).query(Integer.class).single();
            if (total != null && total > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Lo uso alguna vez algun registro, aunque ya no?
     *
     * <p>Si se borrara, el historial que lo menciona quedaria apuntando a un valor
     * inexistente y dejaria de poder explicarse.
     */
    public boolean enUsoHistorico(CatalogDefinition catalogo, UUID id) {
        String filtroClase = catalogo.discriminador() == null
                ? ""
                : " AND catalog_kind = '" + catalogo.discriminador() + "'";

        Integer total = jdbc.sql("SELECT count(*) FROM " + catalogo.tablaHistorial()
                        + " WHERE " + catalogo.columnaHistorial() + " = :id" + filtroClase)
                .param("id", id).query(Integer.class).single();
        return total != null && total > 0;
    }
}
