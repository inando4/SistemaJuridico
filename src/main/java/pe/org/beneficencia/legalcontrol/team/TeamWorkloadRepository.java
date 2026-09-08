package pe.org.beneficencia.legalcontrol.team;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * La carga de todo el equipo, en <b>una sola consulta</b>.
 *
 * <p>Cuatro cifras por persona no son cuatro consultas por persona. Con la base en
 * otra region, un viaje por abogado seria un N+1 que hoy no duele con cinco y
 * dolera con quince (principio IV). {@code count(*) FILTER} las resuelve todas en
 * un recorrido, y el numero de consultas no depende del tamaño del equipo.
 *
 * <p>Aqui no se cuentan dias habiles: las fronteras llegan resueltas desde fuera.
 */
@Repository
public class TeamWorkloadRepository {

    /** Igual que en el panel: cumplido no es activo, aunque conserve {@code active}. */
    private static final String ACTIVO = "(t.active AND t.completed_at IS NULL)";

    private final JdbcClient jdbc;

    public TeamWorkloadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param hace15 fecha a quince dias habiles atras, o null si falta calendario
     */
    public List<Fila> cargaDelEquipo(LocalDate hoy, LocalDate lunes, LocalDate domingo,
                                     LocalDate hace15) {
        return jdbc.sql("""
                SELECT u.id, u.name, u.role,
                  count(t.id) FILTER (WHERE %1$s AND t.deadline < :hoy)      AS vencidos,
                  count(t.id) FILTER (WHERE %1$s AND (
                        (t.deadline      BETWEEN :lunes AND :domingo) OR
                        (t.scheduled_for BETWEEN :lunes AND :domingo)))      AS esta_semana,
                  count(t.id) FILTER (WHERE %1$s AND t.deadline IS NULL
                        AND t.received_at < CAST(:hace15 AS date))           AS sin_plazo_antiguos,
                  count(t.id) FILTER (WHERE %1$s)                            AS activos
                FROM app_user u
                -- LEFT, no JOIN: quien no tiene nada tiene que salir con ceros, que es
                -- justo la persona a la que se le puede asignar trabajo.
                LEFT JOIN pending_task t ON t.owner_id = u.id
                -- Por cuenta activa, NO por rol: la jefa lleva expedientes y filtrarla
                -- la borraria de la vista que sirve para repartir el trabajo del area.
                WHERE u.status = 'ACTIVE'
                GROUP BY u.id, u.name, u.role
                ORDER BY (count(t.id) FILTER (WHERE %1$s AND t.deadline < :hoy)
                        + count(t.id) FILTER (WHERE %1$s AND (
                              (t.deadline      BETWEEN :lunes AND :domingo) OR
                              (t.scheduled_for BETWEEN :lunes AND :domingo)))) DESC,
                         count(t.id) FILTER (WHERE %1$s) DESC,
                         lower(btrim(u.name)), u.id
                """.formatted(ACTIVO))
                .param("hoy", hoy).param("lunes", lunes).param("domingo", domingo)
                // Sin CAST, PostgreSQL no puede inferir el tipo de un parametro nulo.
                .param("hace15", hace15)
                .query(Fila.class)
                .list();
    }

    /**
     * Lo que devuelve la consulta.
     *
     * <p>{@code sinPlazoAntiguos} sale en cero cuando falta calendario, porque la
     * comparacion contra null no casa con nada. Distinguir ese cero del cero real
     * es cosa del controlador, que sabe si la frontera existia.
     */
    public record Fila(java.util.UUID id, String name, String role,
                       int vencidos, int estaSemana, int sinPlazoAntiguos, int activos) {
    }
}
