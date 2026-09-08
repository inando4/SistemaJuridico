package pe.org.beneficencia.legalcontrol.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Los pendientes que requieren atencion, clasificados y ordenados (insumo, 24 y 35).
 *
 * <p><b>Un solo CASE, no cinco consultas.</b> Unir cinco selects daria duplicados
 * en cuanto un pendiente este vencido y ademas programado para hoy, que es un caso
 * corriente; y serian cinco viajes para pintar una pantalla. El CASE evalua en
 * orden y se queda con la primera coincidencia, que es exactamente la semantica de
 * «el nivel mas urgente» que pide FR-013.
 *
 * <p>Las fronteras de dias habiles llegan resueltas desde fuera: aqui solo se
 * comparan fechas (principio VI).
 */
@Repository
public class AlertRepository {

    private static final String NIVEL = """
            CASE
              WHEN t.deadline < :hoy                       THEN 1
              WHEN t.deadline = :hoy                       THEN 2
              WHEN t.scheduled_for = :hoy                  THEN 3
              WHEN t.deadline > :hoy
                   AND t.deadline <= CAST(:frontera3 AS date) THEN 4
              WHEN t.deadline IS NULL
                   AND t.received_at < CAST(:hace15 AS date)  THEN 5
            END
            """;

    private final JdbcClient jdbc;

    public AlertRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param frontera3 tercer dia habil desde hoy, o null si falta calendario
     * @param hace15    quince dias habiles atras, o null si falta calendario
     */
    public List<PendienteConAlerta> deResponsable(UUID responsable, LocalDate hoy,
                                                  LocalDate frontera3, LocalDate hace15,
                                                  Paging pagina) {
        return jdbc.sql("""
                SELECT t.id, t.title,
                       t.deadline, t.scheduled_for, t.received_at,
                       tipo.name  AS tipo,
                       prio.name  AS prioridad,
                       jc.case_number AS expediente_judicial,
                       ap.file_number AS expediente_administrativo,
                       %s AS nivel
                FROM pending_task t
                LEFT JOIN pending_task_type tipo ON tipo.id = t.pending_task_type_id
                LEFT JOIN priority          prio ON prio.id = t.priority_id
                LEFT JOIN judicial_case            jc ON jc.id = t.judicial_case_id
                LEFT JOIN administrative_procedure ap ON ap.id = t.administrative_procedure_id
                WHERE t.owner_id = :responsable
                  AND t.active
                  AND t.completed_at IS NULL
                  AND (%s) IS NOT NULL
                ORDER BY nivel ASC,
                         coalesce(t.deadline, t.scheduled_for, t.received_at) ASC,
                         t.id ASC
                LIMIT :limite OFFSET :salto
                """.formatted(NIVEL, NIVEL))
                .param("responsable", responsable)
                .param("hoy", hoy)
                // Sin CAST, PostgreSQL no infiere el tipo de un parametro nulo.
                .param("frontera3", frontera3)
                .param("hace15", hace15)
                .param("limite", pagina.limitConSondeo())
                .param("salto", pagina.offset())
                .query(Fila.class)
                .list()
                .stream()
                .map(PendienteConAlerta::de)
                .toList();
    }

    /** Lo que devuelve la consulta, con el nivel todavia como numero. */
    public record Fila(UUID id, String title, LocalDate deadline, LocalDate scheduledFor,
                       LocalDate receivedAt, String tipo, String prioridad,
                       String expedienteJudicial, String expedienteAdministrativo, int nivel) {
    }

    /** Un pendiente ya clasificado, listo para la pantalla. */
    public record PendienteConAlerta(UUID id, String titulo, LocalDate deadline,
                                     LocalDate scheduledFor, LocalDate receivedAt,
                                     String tipo, String prioridad,
                                     String expedienteJudicial, String expedienteAdministrativo,
                                     NivelDeAlerta nivel) {

        static PendienteConAlerta de(Fila f) {
            return new PendienteConAlerta(f.id(), f.title(), f.deadline(), f.scheduledFor(),
                    f.receivedAt(), f.tipo(), f.prioridad(), f.expedienteJudicial(),
                    f.expedienteAdministrativo(), NivelDeAlerta.desdeOrden(f.nivel()));
        }

        /** De que expediente cuelga, si cuelga de alguno. */
        public String expediente() {
            if (expedienteJudicial != null) {
                return expedienteJudicial;
            }
            return expedienteAdministrativo;
        }
    }
}
