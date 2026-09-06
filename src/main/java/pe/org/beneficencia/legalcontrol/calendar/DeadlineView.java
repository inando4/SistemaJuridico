package pe.org.beneficencia.legalcontrol.calendar;

import java.time.LocalDate;
import java.util.List;

/**
 * Interpretacion de una fecha limite. Se calcula al consultar y se descarta:
 * NUNCA se guarda en la base, porque el resultado cambia cada dia (principio V).
 *
 * @param estado             VENCIDO, VENCE_HOY, PENDIENTE, SIN_FECHA o SIN_CALENDARIO
 * @param diasHabiles        dias habiles restantes, o null si no se pudo calcular
 * @param limiteNoHabil      la fecha limite cae en sabado, domingo o dia no laborable
 * @param anosSinCobertura   anos del intervalo cuyo calendario no esta revisado
 * @param fechaReferencia    el «hoy» con el que se calculo, visible en pantalla
 */
public record DeadlineView(
        Estado estado,
        Integer diasHabiles,
        boolean limiteNoHabil,
        List<Integer> anosSinCobertura,
        LocalDate fechaReferencia) {

    public enum Estado { VENCIDO, VENCE_HOY, PENDIENTE, SIN_FECHA, SIN_CALENDARIO }

    /** Texto en espanol; el color nunca es la unica senal (FR-021). */
    public String texto() {
        return switch (estado) {
            case SIN_FECHA -> "Sin fecha limite";
            case VENCIDO -> "Vencido";
            case VENCE_HOY -> "Vence hoy";
            case SIN_CALENDARIO -> "Calculo no disponible: revisar dias no laborables";
            case PENDIENTE -> diasHabiles == 1
                    ? "1 dia habil restante"
                    : diasHabiles + " dias habiles restantes";
        };
    }

    /** Clase CSS, siempre acompanada del texto de arriba. */
    public String clase() {
        return switch (estado) {
            case VENCIDO -> "vencido";
            case VENCE_HOY -> "vence-hoy";
            case SIN_FECHA, SIN_CALENDARIO -> "sin-plazo";
            case PENDIENTE -> "";
        };
    }

    public String anosFaltantes() {
        return anosSinCobertura.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
