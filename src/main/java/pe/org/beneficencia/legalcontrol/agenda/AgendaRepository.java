package pe.org.beneficencia.legalcontrol.agenda;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Los eventos de un rango de fechas, en <b>una sola consulta</b>.
 *
 * <p>Cinco ramas unidas, una por cada columna de fecha que produce un evento. La
 * alternativa natural —preguntar por cada dia de la vista— serian 31 consultas para
 * un mes y 1 para un dia, y con datos de prueba nadie lo nota. Con una consulta por
 * rango, el mes cuesta lo mismo que el dia, que es lo que {@code AgendaQueryBudgetIT}
 * comprueba.
 *
 * <p><b>El calendario no pagina, y es deliberado.</b> Una rejilla mensual incompleta
 * engaña: o estan todos los eventos del mes o hay dias que mienten. El volumen lo
 * acota la realidad —lo que cinco personas tienen en un mes—, y como el numero de
 * consultas es 1 pase lo que pase, la invariante no vigila esto: lo vigila el tiempo.
 */
@Repository
public class AgendaRepository {

    private static final String CONSULTA = """
            SELECT e.dia, e.tipo, e.entidad, e.entidad_id, e.titulo,
                   e.tipo_de_pendiente, e.owner_id, e.responsable
            FROM (
                -- 1. Pendientes con fecha programada
                SELECT t.scheduled_for AS dia, 'PROGRAMADO' AS tipo,
                       'PENDING_TASK' AS entidad, t.id AS entidad_id, t.title AS titulo,
                       tp.name AS tipo_de_pendiente, t.owner_id, u.name AS responsable
                  FROM pending_task t
                  JOIN app_user u ON u.id = t.owner_id
                  LEFT JOIN pending_task_type tp ON tp.id = t.pending_task_type_id
                 WHERE t.active AND t.completed_at IS NULL
                   AND t.scheduled_for BETWEEN :desde AND :hasta

                UNION ALL
                -- 2. Vencimientos de pendientes
                SELECT t.deadline, 'VENCIMIENTO', 'PENDING_TASK', t.id, t.title,
                       tp.name, t.owner_id, u.name
                  FROM pending_task t
                  JOIN app_user u ON u.id = t.owner_id
                  LEFT JOIN pending_task_type tp ON tp.id = t.pending_task_type_id
                 WHERE t.active AND t.completed_at IS NULL
                   AND t.deadline BETWEEN :desde AND :hasta

                UNION ALL
                -- 3. Vencimientos de expedientes judiciales
                SELECT c.deadline, 'VENCIMIENTO', 'JUDICIAL_CASE', c.id, c.case_number,
                       NULL, c.owner_id, u.name
                  FROM judicial_case c
                  JOIN app_user u ON u.id = c.owner_id
                 WHERE c.active AND c.deadline BETWEEN :desde AND :hasta

                UNION ALL
                -- 4. Actuaciones judiciales. Es la unica fecha que el sistema guarda
                -- sobre ellas, y es pasada: son hechos, no previsiones.
                SELECT c.last_action_date, 'ACTUACION', 'JUDICIAL_CASE', c.id, c.case_number,
                       NULL, c.owner_id, u.name
                  FROM judicial_case c
                  JOIN app_user u ON u.id = c.owner_id
                 WHERE c.active AND c.last_action_date BETWEEN :desde AND :hasta

                UNION ALL
                -- 5. Vencimientos de procedimientos administrativos
                SELECT p.deadline, 'VENCIMIENTO', 'ADMINISTRATIVE_PROCEDURE', p.id, p.file_number,
                       NULL, p.owner_id, u.name
                  FROM administrative_procedure p
                  JOIN app_user u ON u.id = p.owner_id
                 WHERE p.active AND p.deadline BETWEEN :desde AND :hasta
            ) e
            WHERE CAST(:persona AS uuid) IS NULL OR e.owner_id = CAST(:persona AS uuid)
            ORDER BY e.dia ASC, e.tipo ASC, e.titulo ASC, e.entidad_id ASC
            """;

    private final JdbcClient jdbc;

    public AgendaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param persona responsable por el que filtrar, o {@code null} para toda el area.
     *                El {@code CAST} es necesario: PostgreSQL no infiere el tipo de un
     *                parametro nulo, el mismo tropiezo que la 004 documento.
     */
    public List<EventoDeAgenda> eventos(LocalDate desde, LocalDate hasta, UUID persona) {
        return jdbc.sql(CONSULTA)
                .param("desde", desde).param("hasta", hasta).param("persona", persona)
                .query(EventoDeAgenda.class).list();
    }
}
