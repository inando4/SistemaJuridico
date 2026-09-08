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

    /**
     * Dias habiles a partir de los cuales un pendiente sin plazo se senala.
     *
     * <p>La seccion 19 del insumo pide avisar cuando se supere este numero, de modo
     * que el aviso aparece a partir del decimosexto dia habil.
     */
    public static final int UMBRAL_SIN_PLAZO = 15;

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

    /**
     * Dias habiles transcurridos entre dos fechas, excluyendo la primera e
     * incluyendo la ultima.
     *
     * <p>Es la antiguedad de un pendiente sin fecha limite, que la seccion 19 mide
     * desde su fecha de recepcion.
     *
     * <p>Devuelve vacio si falta cobertura de calendario para algun ano del
     * intervalo: una antiguedad calculada sobre dias no revisados parece un dato y
     * no lo es.
     */
    /**
     * Fecha del n-esimo dia habil posterior a la dada.
     *
     * <p>Existe para que las pantallas de resumen no tengan que contar dias habiles
     * fila a fila. Se resuelve <b>la frontera</b> una vez y la consulta compara
     * fechas normales: la aritmetica de dias habiles sigue viviendo solo aqui
     * (principio VI) y el listado no hace una consulta por pendiente (principio IV).
     *
     * <p>Coherente con {@link #diasHabilesTranscurridos}: excluye el dia de partida
     * y cuenta solo dias habiles.
     *
     * @return la fecha, o vacio si falta cobertura de calendario en el camino
     */
    public java.util.Optional<LocalDate> sumarDiasHabiles(LocalDate desde, int n,
                                                          CalendarSnapshot calendario) {
        if (desde == null || n < 0) {
            return java.util.Optional.empty();
        }
        if (n == 0) {
            return java.util.Optional.of(desde);
        }
        LocalDate tope = desde.plusDays(n * 7L + 400);
        int habiles = 0;
        for (LocalDate dia = desde.plusDays(1); !dia.isAfter(tope); dia = dia.plusDays(1)) {
            if (!calendario.cubre(dia.getYear())) {
                return java.util.Optional.empty();
            }
            if (calendario.esHabil(dia)) {
                habiles++;
                if (habiles == n) {
                    return java.util.Optional.of(dia);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Fecha desde la cual han transcurrido exactamente n dias habiles hasta la dada.
     *
     * <p>La usa el aviso de pendiente sin plazo: en vez de contar la antiguedad de
     * cada pendiente, se calcula una sola vez la fecha frontera y se comparan las
     * recepciones contra ella.
     *
     * <p><b>Cuidado con el borde:</b> una recepcion <i>igual</i> a esta fecha lleva
     * exactamente n dias habiles, y el insumo pide avisar cuando se superen. La
     * condicion equivalente a {@code diasHabilesTranscurridos(r, hasta) > n} es por
     * tanto {@code r < restarDiasHabiles(hasta, n)}, con menor estricto.
     *
     * @return la fecha, o vacio si falta cobertura de calendario en el camino
     */
    public java.util.Optional<LocalDate> restarDiasHabiles(LocalDate hasta, int n,
                                                           CalendarSnapshot calendario) {
        if (hasta == null || n < 0) {
            return java.util.Optional.empty();
        }
        if (n == 0) {
            return java.util.Optional.of(hasta);
        }
        LocalDate tope = hasta.minusDays(n * 7L + 400);
        int habiles = 0;
        for (LocalDate dia = hasta; !dia.isBefore(tope); dia = dia.minusDays(1)) {
            if (!calendario.cubre(dia.getYear())) {
                return java.util.Optional.empty();
            }
            if (calendario.esHabil(dia)) {
                habiles++;
                if (habiles == n) {
                    // Se devuelve el dia anterior al n-esimo habil contado hacia
                    // atras: contar de el a «hasta» da exactamente n, porque
                    // diasHabilesTranscurridos excluye su punto de partida.
                    return java.util.Optional.of(dia.minusDays(1));
                }
            }
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<Integer> diasHabilesTranscurridos(LocalDate desde, LocalDate hasta,
                                                                CalendarSnapshot calendario) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            return java.util.Optional.empty();
        }
        for (int ano = desde.getYear(); ano <= hasta.getYear(); ano++) {
            if (!calendario.cubre(ano)) {
                return java.util.Optional.empty();
            }
        }
        int habiles = 0;
        for (LocalDate dia = desde.plusDays(1); !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            if (calendario.esHabil(dia)) {
                habiles++;
            }
        }
        return java.util.Optional.of(habiles);
    }
}
