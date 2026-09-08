package pe.org.beneficencia.legalcontrol.team;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Los limites de la semana en curso.
 *
 * <p><b>Aritmetica de calendario, no dias habiles.</b> Es la diferencia que decide
 * que ocurre cuando el ano no esta revisado: contar «lo que vence esta semana» es
 * comparar fechas contra un lunes y un domingo, asi que el recuento sigue siendo
 * correcto sin calendario. Solo «lleva mucho esperando» necesita dias habiles, y
 * ese si se degrada (principio VI).
 *
 * <p>La semana va de lunes a domingo. El fin de semana pertenece a <b>su</b> semana,
 * no a la siguiente: un plazo del sabado es de la semana que termina, y quien lo
 * tenga lo tiene encima ya.
 */
public final class SemanaDeTrabajo {

    private SemanaDeTrabajo() {
    }

    /** El lunes de la semana a la que pertenece la fecha; ella misma si es lunes. */
    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** El domingo de esa misma semana. */
    public static LocalDate domingoDe(LocalDate fecha) {
        return lunesDe(fecha).plusDays(6);
    }
}
