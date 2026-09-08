package pe.org.beneficencia.legalcontrol.dashboard;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Las seis cuentas del dashboard, en <b>una sola consulta</b>.
 *
 * <p>Seis tarjetas no son seis consultas. Con la base en otra region, media docena
 * de viajes de ida y vuelta para pintar la pantalla de entrada es exactamente la
 * lentitud que motivo dejar el Excel (principio IV). {@code count(*) FILTER} las
 * resuelve todas en un recorrido.
 *
 * <p><b>Aqui no se cuentan dias habiles.</b> Las fronteras llegan ya resueltas
 * desde {@code DeadlineEvaluator}, que es la unica funcion que sabe que es un dia
 * habil (principio VI). Este repositorio solo compara fechas.
 */
@Repository
public class DashboardRepository {

    /**
     * Un pendiente esta activo cuando no esta archivado <b>ni cumplido</b>.
     *
     * <p>La segunda mitad importa: {@code active} es la columna de archivado y un
     * pendiente cumplido la conserva en true. Contar solo por ella haria que la
     * tarjeta discrepara del listado al que enlaza.
     */
    private static final String ACTIVO = "(t.active AND t.completed_at IS NULL)";

    private final JdbcClient jdbc;

    public DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param frontera3 fecha del tercer dia habil desde hoy, o null si falta calendario
     * @param hace15    fecha a quince dias habiles atras, o null si falta calendario
     */
    public Cuentas contar(UUID responsable, LocalDate hoy, LocalDate frontera3,
                          LocalDate hace15, LocalDate inicioDeMes) {
        return jdbc.sql("""
                SELECT
                  count(*) FILTER (WHERE %1$s
                        AND (t.deadline = :hoy OR t.scheduled_for = :hoy))    AS urgentes_hoy,
                  count(*) FILTER (WHERE %1$s AND t.deadline < :hoy)          AS vencidos,
                  count(*) FILTER (WHERE %1$s AND t.deadline > :hoy
                        AND t.deadline <= CAST(:frontera3 AS date))           AS proximos,
                  count(*) FILTER (WHERE %1$s AND t.deadline IS NULL
                        AND t.received_at < CAST(:hace15 AS date))            AS sin_plazo,
                  count(*) FILTER (WHERE %1$s)                                AS activos,
                  count(*) FILTER (WHERE t.completed_at >= CAST(:inicioDeMes AS date))
                                                                              AS cumplidos_mes
                FROM pending_task t
                WHERE t.owner_id = :responsable
                """.formatted(ACTIVO))
                .param("responsable", responsable)
                .param("hoy", hoy)
                // Sin CAST, PostgreSQL no puede inferir el tipo de un parametro nulo.
                .param("frontera3", frontera3)
                .param("hace15", hace15)
                .param("inicioDeMes", inicioDeMes)
                .query(Cuentas.class)
                .single();
    }

    /**
     * Lo que devuelve la consulta, con las seis cifras siempre presentes.
     *
     * <p>Cuando falta calendario, «proximos» y «sinPlazo» salen en cero porque la
     * comparacion contra null no casa con nada. Distinguir ese cero del cero real
     * es cosa del controlador, que sabe si la frontera existia.
     */
    public record Cuentas(int urgentesHoy, int vencidos, int proximos, int sinPlazo,
                          int activos, int cumplidosMes) {
    }
}
