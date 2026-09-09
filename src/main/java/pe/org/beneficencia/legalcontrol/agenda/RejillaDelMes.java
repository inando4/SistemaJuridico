package pe.org.beneficencia.legalcontrol.agenda;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El rango de cada vista y el reparto de los eventos en dias y semanas.
 *
 * <p><b>Sin SQL.</b> La consulta devuelve eventos con fecha; agruparlos es aritmetica
 * sobre la lista. Repartirlos aqui es lo que permite que las tres vistas compartan una
 * sola consulta.
 *
 * <p>La semana va de lunes a domingo, igual que en {@code SemanaDeTrabajo}: es
 * aritmetica de calendario y no interviene ningun dia habil.
 */
public final class RejillaDelMes {

    public static final String DIA = "dia";
    public static final String SEMANA = "semana";
    public static final String MES = "mes";

    private RejillaDelMes() {
    }

    public static String vistaValida(String vista) {
        if (SEMANA.equals(vista) || DIA.equals(vista)) {
            return vista;
        }
        return MES;
    }

    public static LocalDate desde(String vista, LocalDate ancla) {
        return switch (vistaValida(vista)) {
            case DIA -> ancla;
            case SEMANA -> lunes(ancla);
            // La rejilla del mes empieza el lunes anterior al dia 1: esos dias vecinos
            // se pintan, asi que se piden en el mismo viaje.
            default -> lunes(ancla.withDayOfMonth(1));
        };
    }

    public static LocalDate hasta(String vista, LocalDate ancla) {
        return switch (vistaValida(vista)) {
            case DIA -> ancla;
            case SEMANA -> lunes(ancla).plusDays(6);
            default -> lunes(ancla.with(TemporalAdjusters.lastDayOfMonth())).plusDays(6);
        };
    }

    /** El ancla del periodo anterior, para la navegacion. */
    public static LocalDate anterior(String vista, LocalDate ancla) {
        return switch (vistaValida(vista)) {
            case DIA -> ancla.minusDays(1);
            case SEMANA -> ancla.minusWeeks(1);
            default -> ancla.minusMonths(1);
        };
    }

    public static LocalDate siguiente(String vista, LocalDate ancla) {
        return switch (vistaValida(vista)) {
            case DIA -> ancla.plusDays(1);
            case SEMANA -> ancla.plusWeeks(1);
            default -> ancla.plusMonths(1);
        };
    }

    /** Todos los dias del rango, en orden, incluidos los vacios. */
    public static List<LocalDate> dias(LocalDate desde, LocalDate hasta) {
        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            dias.add(d);
        }
        return dias;
    }

    /** Los dias agrupados en filas de siete, para la rejilla del mes. */
    public static List<List<LocalDate>> semanas(LocalDate desde, LocalDate hasta) {
        List<List<LocalDate>> filas = new ArrayList<>();
        List<LocalDate> actual = new ArrayList<>();
        for (LocalDate d : dias(desde, hasta)) {
            actual.add(d);
            if (actual.size() == 7) {
                filas.add(List.copyOf(actual));
                actual.clear();
            }
        }
        if (!actual.isEmpty()) {
            filas.add(List.copyOf(actual));
        }
        return filas;
    }

    /**
     * Los eventos indexados por dia.
     *
     * <p>Un {@code LinkedHashMap} conserva el orden de la consulta dentro de cada dia,
     * que ya viene desempatado de forma estable.
     */
    public static Map<LocalDate, List<EventoDeAgenda>> porDia(List<EventoDeAgenda> eventos) {
        Map<LocalDate, List<EventoDeAgenda>> indice = new LinkedHashMap<>();
        for (EventoDeAgenda e : eventos) {
            indice.computeIfAbsent(e.dia(), d -> new ArrayList<>()).add(e);
        }
        return indice;
    }

    private static LocalDate lunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
