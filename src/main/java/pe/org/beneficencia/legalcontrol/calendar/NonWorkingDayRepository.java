package pe.org.beneficencia.legalcontrol.calendar;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Dias no laborables y la revision tecnica de cada ano. */
@Repository
public class NonWorkingDayRepository {

    /** Tipos en ingles internamente, en espanol en pantalla (principio I). */
    public enum Tipo {
        NATIONAL_HOLIDAY("Feriado nacional"),
        REGIONAL_HOLIDAY("Feriado regional"),
        NON_WORKING_DAY("Día no laborable"),
        OTHER("Otro");

        public final String etiqueta;

        Tipo(String etiqueta) {
            this.etiqueta = etiqueta;
        }
    }

    private final JdbcClient jdbc;

    public NonWorkingDayRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> delAno(int ano) {
        return jdbc.sql("""
                SELECT id, day, description, kind, version
                FROM non_working_day
                WHERE EXTRACT(YEAR FROM day) = :ano
                ORDER BY day
                """).param("ano", ano).query().listOfRows();
    }

    public int contarDelAno(int ano) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM non_working_day WHERE EXTRACT(YEAR FROM day) = :ano
                """).param("ano", ano).query(Integer.class).single();
        return total == null ? 0 : total;
    }

    /**
     * Asegura la fila del ano y sube su revision, invalidando cualquier revision
     * anterior. Se bloquea primero para que dos cambios simultaneos no se pisen.
     */
    public long tocarAno(int ano, UUID actor, Instant ahora) {
        jdbc.sql("""
                INSERT INTO calendar_year (year, revision, created_by, created_at)
                VALUES (:ano, 0, :actor, :ahora)
                ON CONFLICT (year) DO NOTHING
                """).param("ano", ano).param("actor", actor)
                .param("ahora", Timestamp.from(ahora)).update();

        return jdbc.sql("""
                UPDATE calendar_year SET revision = revision + 1
                WHERE year = :ano
                RETURNING revision
                """).param("ano", ano).query(Long.class).single();
    }

    public Optional<Long> revisionActual(int ano) {
        return jdbc.sql("SELECT revision FROM calendar_year WHERE year = :ano FOR UPDATE")
                .param("ano", ano).query(Long.class).optional();
    }

    public UUID insertar(LocalDate dia, String descripcion, Tipo tipo, UUID actor, Instant ahora) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO non_working_day (id, day, description, kind, created_by,
                                             created_at, updated_at, version)
                VALUES (:id, :dia, :desc, :tipo, :actor, :ahora, :ahora, 1)
                """)
                .param("id", id).param("dia", dia).param("desc", descripcion.strip())
                .param("tipo", tipo.name()).param("actor", actor)
                .param("ahora", Timestamp.from(ahora)).update();
        return id;
    }

    public Optional<Map<String, Object>> porId(UUID id) {
        return jdbc.sql("SELECT id, day, description, kind, version FROM non_working_day WHERE id = :id")
                .param("id", id).query().listOfRows().stream().findFirst();
    }

    public boolean actualizar(UUID id, LocalDate dia, String descripcion, Tipo tipo,
                              long version, Instant ahora) {
        return jdbc.sql("""
                UPDATE non_working_day
                SET day = :dia, description = :desc, kind = :tipo,
                    updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id).param("dia", dia).param("desc", descripcion.strip())
                .param("tipo", tipo.name()).param("version", version)
                .param("ahora", Timestamp.from(ahora)).update() == 1;
    }

    public boolean eliminar(UUID id, long version) {
        return jdbc.sql("DELETE FROM non_working_day WHERE id = :id AND version = :version")
                .param("id", id).param("version", version).update() == 1;
    }

    public boolean diaYaRegistrado(LocalDate dia, UUID excepto) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM non_working_day
                WHERE day = :dia AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid))
                """).param("dia", dia).param("excepto", excepto).query(Integer.class).single();
        return total != null && total > 0;
    }
}
