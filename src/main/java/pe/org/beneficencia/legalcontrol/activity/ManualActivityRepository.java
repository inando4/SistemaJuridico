package pe.org.beneficencia.legalcontrol.activity;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lectura y escritura de las actividades manuales. */
@Repository
public class ManualActivityRepository {

    /**
     * El {@code LEFT JOIN} no es un detalle de estilo.
     *
     * <p>Con {@code JOIN} desaparecerian justo las dos formas que la 006 vino a
     * permitir: las actividades sin tipo y las de tipo escrito a mano, que no tienen
     * fila en el catalogo. Serian invisibles sin ningun error a la vista.
     */
    private static final String SELECCION = """
            SELECT a.id, a.owner_id, u.name AS owner_name, a.performed_on, a.description,
                   a.pending_task_type_id, tipo.name AS pending_task_type_name,
                   a.other_type, a.active, a.created_at, a.updated_at, a.version
            FROM manual_activity a
            JOIN app_user u ON u.id = a.owner_id
            LEFT JOIN pending_task_type tipo ON tipo.id = a.pending_task_type_id
            """;

    private final JdbcClient jdbc;

    public ManualActivityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Las actividades vigentes de una persona en un dia. */
    public List<ManualActivity> delDia(UUID responsable, LocalDate dia) {
        return jdbc.sql(SELECCION + """
                 WHERE a.owner_id = :responsable AND a.performed_on = :dia AND a.active
                 ORDER BY a.created_at ASC, a.id ASC
                """)
                .param("responsable", responsable).param("dia", dia)
                .query(ManualActivity.class).list();
    }

    public Optional<ManualActivity> porId(UUID id) {
        return jdbc.sql(SELECCION + " WHERE a.id = :id")
                .param("id", id).query(ManualActivity.class).optional();
    }

    public UUID insertar(ManualActivityForm form, UUID responsable, Instant ahora) {
        UUID id = UUID.randomUUID();
        Timestamp momento = Timestamp.from(ahora);
        jdbc.sql("""
                INSERT INTO manual_activity
                    (id, owner_id, performed_on, description, pending_task_type_id,
                     other_type, active, created_at, updated_at, version)
                VALUES
                    (:id, :responsable, :dia, :descripcion, :tipo, :otro, true,
                     :ahora, :ahora, 1)
                """)
                .param("id", id).param("responsable", responsable)
                .param("dia", form.performedOn()).param("descripcion", form.description().strip())
                .param("tipo", form.typeId()).param("otro", form.otroTipoNormalizado())
                .param("ahora", momento).update();
        return id;
    }

    /** @return false si la version no coincide: otra persona lo modifico antes. */
    public boolean actualizar(UUID id, ManualActivityForm form, long version, Instant ahora) {
        return jdbc.sql("""
                UPDATE manual_activity
                   SET performed_on = :dia, description = :descripcion,
                       pending_task_type_id = :tipo, other_type = :otro,
                       updated_at = :ahora, version = version + 1
                 WHERE id = :id AND version = :version
                """)
                .param("id", id).param("dia", form.performedOn())
                .param("descripcion", form.description().strip())
                .param("tipo", form.typeId()).param("otro", form.otroTipoNormalizado())
                .param("version", version).param("ahora", Timestamp.from(ahora))
                .update() == 1;
    }

    /**
     * Retirar es {@code active = false}.
     *
     * <p>Nunca un {@code DELETE}: la V10 se lo revoca al rol de la aplicacion, asi que
     * no depende de que este metodo se porte bien.
     */
    public boolean retirar(UUID id, long version, Instant ahora) {
        return jdbc.sql("""
                UPDATE manual_activity
                   SET active = false, updated_at = :ahora, version = version + 1
                 WHERE id = :id AND version = :version AND active
                """)
                .param("id", id).param("version", version)
                .param("ahora", Timestamp.from(ahora)).update() == 1;
    }
}
