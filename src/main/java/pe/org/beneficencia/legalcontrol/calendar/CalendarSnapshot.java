package pe.org.beneficencia.legalcontrol.calendar;

import java.time.LocalDate;
import java.util.Set;

/**
 * Los dias no laborables y los anos con cobertura revisada, leidos <b>una sola
 * vez por consulta</b>.
 *
 * <p>Sin esto, un listado de 25 expedientes haria 25 consultas al calendario. Con
 * la base al otro lado del pais eso son veinticinco viajes de ida y vuelta para
 * pintar una pantalla.
 */
public record CalendarSnapshot(Set<LocalDate> diasNoLaborables, Set<Integer> anosCubiertos) {

    public boolean esHabil(LocalDate dia) {
        return switch (dia.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> !diasNoLaborables.contains(dia);
        };
    }

    public boolean cubre(int ano) {
        return anosCubiertos.contains(ano);
    }
}
