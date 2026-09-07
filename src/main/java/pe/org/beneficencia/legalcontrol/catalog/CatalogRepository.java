package pe.org.beneficencia.legalcontrol.catalog;

import java.sql.Timestamp;
import java.time.Instant;
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
    public List<Map<String, Object>> habilitados(CatalogDefinition catalogo) {
        return jdbc.sql("SELECT id, name FROM " + catalogo.tabla()
                + " WHERE enabled = true ORDER BY lower(btrim(name))").query().listOfRows();
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

    /** ¿Lo usa algun registro ahora mismo? */
    public boolean enUsoActual(CatalogDefinition catalogo, UUID id) {
        Integer total = jdbc.sql("SELECT count(*) FROM " + catalogo.tablaEnUso()
                        + " WHERE " + catalogo.columnaEnUso() + " = :id")
                .param("id", id).query(Integer.class).single();
        return total != null && total > 0;
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
