package pe.org.beneficencia.legalcontrol.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.stickynote.NoteColor;
import pe.org.beneficencia.legalcontrol.stickynote.StickyNote;

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
        return leerPanel(responsable, hoy, frontera3, hace15, inicioDeMes).cuentas();
    }

    /**
     * Las seis cifras <b>y los recordatorios pegados</b>, en un solo viaje.
     *
     * <p>Los recordatorios no tienen nada que ver con los pendientes, pero se
     * muestran en la misma pantalla y el panel tiene un presupuesto de cuatro
     * consultas que ya gastaba entero. El {@code LEFT JOIN ... ON true} devuelve
     * una fila por nota repitiendo las cifras, o una sola fila con la nota en null
     * si no hay ninguna: las cuentas salen siempre de la primera.
     */
    public Panel leerPanel(UUID responsable, LocalDate hoy, LocalDate frontera3,
                           LocalDate hace15, LocalDate inicioDeMes) {
        return jdbc.sql("""
                WITH cuentas AS (
                  SELECT
                    count(*) FILTER (WHERE %1$s
                          AND (t.deadline = :hoy OR t.scheduled_for = :hoy))  AS urgentes_hoy,
                    count(*) FILTER (WHERE %1$s AND t.deadline < :hoy)        AS vencidos,
                    count(*) FILTER (WHERE %1$s AND t.deadline > :hoy
                          AND t.deadline <= CAST(:frontera3 AS date))         AS proximos,
                    count(*) FILTER (WHERE %1$s AND t.deadline IS NULL
                          AND t.received_at < CAST(:hace15 AS date))          AS sin_plazo,
                    count(*) FILTER (WHERE %1$s)                              AS activos,
                    count(*) FILTER (WHERE t.completed_at >= CAST(:inicioDeMes AS date))
                                                                              AS cumplidos_mes
                  FROM pending_task t
                  WHERE t.owner_id = :responsable
                ), notas AS (
                  SELECT n.id, n.content, n.color, n.created_at
                  FROM sticky_note n
                  WHERE n.owner_id = :responsable AND n.removed_at IS NULL
                )
                SELECT c.*, n.id AS nota_id, n.content AS nota_texto, n.color AS nota_color,
                       n.created_at AS nota_alta
                FROM cuentas c LEFT JOIN notas n ON true
                ORDER BY n.created_at DESC NULLS LAST, n.id
                """.formatted(ACTIVO))
                .param("responsable", responsable)
                .param("hoy", hoy)
                // Sin CAST, PostgreSQL no puede inferir el tipo de un parametro nulo.
                .param("frontera3", frontera3)
                .param("hace15", hace15)
                .param("inicioDeMes", inicioDeMes)
                .query(rs -> {
                    Cuentas cuentas = null;
                    List<StickyNote> notas = new ArrayList<>();
                    while (rs.next()) {
                        if (cuentas == null) {
                            cuentas = new Cuentas(rs.getInt("urgentes_hoy"), rs.getInt("vencidos"),
                                    rs.getInt("proximos"), rs.getInt("sin_plazo"),
                                    rs.getInt("activos"), rs.getInt("cumplidos_mes"));
                        }
                        UUID nota = rs.getObject("nota_id", UUID.class);
                        if (nota != null) {
                            notas.add(new StickyNote(nota, rs.getString("nota_texto"),
                                    NoteColor.valueOf(rs.getString("nota_color")),
                                    rs.getTimestamp("nota_alta").toInstant()));
                        }
                    }
                    return new Panel(cuentas, List.copyOf(notas));
                });
    }

    /** Lo que pinta el panel: las cifras y los recordatorios, el mas reciente primero. */
    public record Panel(Cuentas cuentas, List<StickyNote> recordatorios) {
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
