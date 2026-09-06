package pe.org.beneficencia.legalcontrol.proceduralstatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Catalogo de situaciones procesales. Arranca vacio: lo llena JEFA. */
@Repository
public class ProceduralStatusRepository {

    private final JdbcClient jdbc;

    public ProceduralStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> todos() {
        return jdbc.sql("""
                SELECT id, name, description, enabled, version
                FROM procedural_status ORDER BY lower(btrim(name))
                """).query().listOfRows();
    }

    /** Solo los habilitados: son los unicos que se ofrecen para elegir de nuevo. */
    public List<Map<String, Object>> habilitados() {
        return jdbc.sql("""
                SELECT id, name FROM procedural_status
                WHERE enabled = true ORDER BY lower(btrim(name))
                """).query().listOfRows();
    }

    public Optional<Map<String, Object>> porId(UUID id) {
        return jdbc.sql("""
                SELECT id, name, description, enabled, version
                FROM procedural_status WHERE id = :id
                """).param("id", id).query().listOfRows().stream().findFirst();
    }

    public UUID insertar(String nombre, String descripcion, UUID actor, Instant ahora) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO procedural_status (id, name, description, enabled, created_by,
                                               created_at, updated_at, version)
                VALUES (:id, :nombre, :desc, true, :actor, :ahora, :ahora, 1)
                """)
                .param("id", id).param("nombre", nombre.strip())
                .param("desc", descripcion == null || descripcion.isBlank() ? null : descripcion.strip())
                .param("actor", actor).param("ahora", Timestamp.from(ahora)).update();
        return id;
    }

    public boolean cambiarDisponibilidad(UUID id, boolean habilitado, long version, Instant ahora) {
        return jdbc.sql("""
                UPDATE procedural_status
                SET enabled = :habilitado, updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version
                """).param("id", id).param("habilitado", habilitado)
                .param("version", version).param("ahora", Timestamp.from(ahora)).update() == 1;
    }

    public boolean eliminar(UUID id, long version) {
        return jdbc.sql("DELETE FROM procedural_status WHERE id = :id AND version = :version")
                .param("id", id).param("version", version).update() == 1;
    }

    public boolean nombreYaUsado(String nombre, UUID excepto) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM procedural_status
                WHERE lower(btrim(name)) = lower(btrim(:nombre))
                  AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid))
                """).param("nombre", nombre).param("excepto", excepto)
                .query(Integer.class).single();
        return total != null && total > 0;
    }

    /** ¿Lo usa algun expediente ahora mismo? */
    public boolean enUsoActual(UUID id) {
        Integer total = jdbc.sql("SELECT count(*) FROM judicial_case WHERE procedural_status_id = :id")
                .param("id", id).query(Integer.class).single();
        return total != null && total > 0;
    }

    /**
     * ¿Lo uso alguna vez algun expediente, aunque ya no?
     *
     * <p>Si se borrara, el historial que lo menciona quedaria apuntando a un
     * estado inexistente y dejaria de poder explicarse.
     */
    public boolean enUsoHistorico(UUID id) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM case_history_status_reference WHERE procedural_status_id = :id
                """).param("id", id).query(Integer.class).single();
        return total != null && total > 0;
    }
}
