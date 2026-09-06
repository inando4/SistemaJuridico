package pe.org.beneficencia.legalcontrol.calendar;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Carga el calendario para una consulta.
 *
 * <p>Dos consultas fijas por pantalla, no una por expediente: los dias no
 * laborables del intervalo y los anos con cobertura revisada.
 */
@Repository
public class CalendarRepository {

    private final JdbcClient jdbc;

    public CalendarRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Instantanea para el intervalo que va desde hoy hasta el ano mas lejano que
     * haga falta.
     *
     * <p>Un ano esta cubierto cuando tiene una revision confirmada <b>para su
     * revision tecnica actual</b>. Si alguien anadio o quito un dia despues de la
     * ultima confirmacion, el ano deja de estar cubierto: el calendario cambio y
     * nadie ha vuelto a mirarlo.
     */
    public CalendarSnapshot instantanea(int anoDesde, int anoHasta) {
        List<LocalDate> dias = jdbc.sql("""
                SELECT day FROM non_working_day
                WHERE EXTRACT(YEAR FROM day) BETWEEN :desde AND :hasta
                """)
                .param("desde", anoDesde).param("hasta", anoHasta)
                .query(LocalDate.class).list();

        List<Integer> cubiertos = jdbc.sql("""
                SELECT y.year
                FROM calendar_year y
                JOIN calendar_review r
                  ON r.year = y.year AND r.reviewed_revision = y.revision
                WHERE y.year BETWEEN :desde AND :hasta
                  AND EXISTS (SELECT 1 FROM non_working_day d
                              WHERE EXTRACT(YEAR FROM d.day) = y.year)
                """)
                .param("desde", anoDesde).param("hasta", anoHasta)
                .query(Integer.class).list();

        return new CalendarSnapshot(new HashSet<>(dias), Set.copyOf(cubiertos));
    }

    /** Instantanea para el ano en curso y unos cuantos por delante. */
    public CalendarSnapshot paraListado(LocalDate hoy) {
        return instantanea(hoy.getYear(), hoy.getYear() + 5);
    }
}
