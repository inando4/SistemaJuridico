package pe.org.beneficencia.legalcontrol.stickynote;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escritura de los recordatorios.
 *
 * <p>La lectura para el panel <b>no esta aqui</b>: viaja en la misma consulta que
 * las seis cifras ({@code DashboardRepository}), para que el panel no pague un
 * viaje mas a la base.
 */
@Repository
public class StickyNoteRepository {

    private final JdbcClient jdbc;

    public StickyNoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int pegadasDe(UUID autor) {
        return jdbc.sql("""
                SELECT count(*) FROM sticky_note
                WHERE owner_id = :autor AND removed_at IS NULL
                """)
                .param("autor", autor).query(Integer.class).single();
    }

    public UUID insertar(UUID autor, String texto, NoteColor color, Instant ahora) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO sticky_note (id, owner_id, content, color, created_at)
                VALUES (:id, :autor, :texto, :color, :ahora)
                """)
                .param("id", id).param("autor", autor).param("texto", texto)
                .param("color", color.name())
                .param("ahora", Timestamp.from(ahora)).update();
        return id;
    }

    /**
     * Quita un recordatorio de su autor. Quitar dos veces no es un error: el
     * segundo clic de un doble clic no debe acabar en una pantalla de fallo.
     *
     * @return false si no existe o es de otra persona
     */
    public boolean quitar(UUID id, UUID autor, Instant ahora) {
        return jdbc.sql("""
                UPDATE sticky_note SET removed_at = coalesce(removed_at, :ahora)
                WHERE id = :id AND owner_id = :autor
                """)
                .param("id", id).param("autor", autor)
                .param("ahora", Timestamp.from(ahora)).update() == 1;
    }
}
