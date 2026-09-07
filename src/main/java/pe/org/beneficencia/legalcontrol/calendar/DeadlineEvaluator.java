package pe.org.beneficencia.legalcontrol.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Unica funcion que interpreta una fecha limite (principio VI).
 *
 * <p>No se reimplementa por pantalla: listado y ficha llaman aqui, para que no
 * puedan discrepar. Dos calculos distintos del mismo plazo es peor que ninguno.
 *
 * <p><b>Regla de conteo:</b> se excluye hoy y se incluye la fecha limite cuando
 * es habil. Contar desde manana evita el clasico error de un dia; incluir el
 * limite refleja que ese dia todavia se puede presentar.
 *
 * <p><b>Un limite no habil no se desplaza.</b> Si vence en sabado, se dice que
 * vence en sabado y se avisa: mover la fecha por cuenta propia seria inventar
 * una regla juridica que a este sistema no le corresponde.
 */
@Component
public class DeadlineEvaluator {

    public DeadlineView evaluar(LocalDate limite, LocalDate hoy, CalendarSnapshot calendario) {
        if (limite == null) {
            return new DeadlineView(DeadlineView.Estado.SIN_FECHA, null, false, List.of(), hoy);
        }

        boolean limiteNoHabil = !calendario.esHabil(limite);

        // Vencido y vence hoy se deciden comparando fechas: no dependen del
        // calendario, asi que se conservan aunque falte cobertura (FR-019).
        if (limite.isBefore(hoy)) {
            return new DeadlineView(DeadlineView.Estado.VENCIDO, null, limiteNoHabil, List.of(), hoy);
        }
        if (limite.isEqual(hoy)) {
            return new DeadlineView(DeadlineView.Estado.VENCE_HOY, null, limiteNoHabil, List.of(), hoy);
        }

        List<Integer> sinCobertura = anosSinCobertura(hoy, limite, calendario);
        if (!sinCobertura.isEmpty()) {
            // Sin calendario verificado no se cuenta: se avisa. Un numero
            // inventado es peor que ninguno, porque parece fiable.
            return new DeadlineView(DeadlineView.Estado.SIN_CALENDARIO, null,
                    limiteNoHabil, sinCobertura, hoy);
        }

        return new DeadlineView(DeadlineView.Estado.PENDIENTE,
                contarHabiles(hoy, limite, calendario), limiteNoHabil, List.of(), hoy);
    }

    /** Dias habiles en el intervalo (hoy, limite]: se excluye hoy, se incluye el limite. */
    private int contarHabiles(LocalDate hoy, LocalDate limite, CalendarSnapshot calendario) {
        int habiles = 0;
        for (LocalDate dia = hoy.plusDays(1); !dia.isAfter(limite); dia = dia.plusDays(1)) {
            if (calendario.esHabil(dia)) {
                habiles++;
            }
        }
        return habiles;
    }

    /** Todos los anos que el intervalo atraviesa deben estar revisados. */
    private List<Integer> anosSinCobertura(LocalDate hoy, LocalDate limite,
                                           CalendarSnapshot calendario) {
        List<Integer> faltantes = new ArrayList<>();
        for (int ano = hoy.getYear(); ano <= limite.getYear(); ano++) {
            if (!calendario.cubre(ano)) {
                faltantes.add(ano);
            }
        }
        return faltantes;
    }

    /**
     * Primer dia habil posterior a la fecha dada.
     *
     * <p>Lo usa la accion «no cumpli» para reprogramar sola (insumo, seccion 16).
     * Vive aqui y no en el servicio de pendientes porque el sistema ya tiene una
     * sola funcion que decide que es un dia habil, y cualquier otra seria una
     * segunda verdad sobre lo mismo (principio VI).
     *
     * <p><b>Devuelve vacio si falta cobertura de calendario</b> para algun ano que
     * haya que atravesar. Reprogramar a un dia que resulto ser feriado es peor que
     * no reprogramar: nadie se entera hasta que llega el vencimiento.
     *
     * @return el siguiente dia habil, o vacio si no se puede determinar con certeza
     */
    public java.util.Optional<LocalDate> siguienteDiaHabil(LocalDate desde,
                                                           CalendarSnapshot calendario) {
        if (desde == null) {
            return java.util.Optional.empty();
        }
        // Un ano entero de margen basta: no existe una racha de dias no habiles
        // mas larga, y evita un bucle sin fin si el calendario estuviera mal.
        LocalDate limite = desde.plusYears(1);

        for (LocalDate dia = desde.plusDays(1); !dia.isAfter(limite); dia = dia.plusDays(1)) {
            if (!calendario.cubre(dia.getYear())) {
                return java.util.Optional.empty();
            }
            if (calendario.esHabil(dia)) {
                return java.util.Optional.of(dia);
            }
        }
        return java.util.Optional.empty();
    }
}
