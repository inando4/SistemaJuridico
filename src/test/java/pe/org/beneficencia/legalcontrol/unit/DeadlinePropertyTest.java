package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;

/**
 * Contrasta el evaluador contra un enumerador dia a dia, escrito aparte y de la
 * forma mas tonta posible.
 *
 * <p>Los doce ejemplos comprueban los casos que alguien penso. Esto comprueba los
 * que nadie penso: cientos de combinaciones al azar de fechas y feriados,
 * incluidos cruces de ano y meses de 28 dias.
 */
class DeadlinePropertyTest {

    private final DeadlineEvaluator evaluador = new DeadlineEvaluator();

    /** Referencia independiente: recorre el intervalo contando a mano. */
    private int contarADedo(LocalDate hoy, LocalDate limite, Set<LocalDate> feriados) {
        int habiles = 0;
        for (LocalDate d = hoy.plusDays(1); !d.isAfter(limite); d = d.plusDays(1)) {
            boolean finDeSemana = d.getDayOfWeek() == DayOfWeek.SATURDAY
                    || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!finDeSemana && !feriados.contains(d)) {
                habiles++;
            }
        }
        return habiles;
    }

    @Test
    @DisplayName("coincide con el conteo manual en 500 combinaciones al azar")
    void coincideConElEnumerador() {
        Random azar = new Random(20260907L);   // semilla fija: fallo reproducible

        for (int caso = 0; caso < 500; caso++) {
            LocalDate hoy = LocalDate.of(2026, 1, 1).plusDays(azar.nextInt(730));
            LocalDate limite = hoy.plusDays(1 + azar.nextInt(400));

            Set<LocalDate> feriados = new HashSet<>();
            for (int f = 0; f < azar.nextInt(15); f++) {
                feriados.add(hoy.plusDays(azar.nextInt(400)));
            }

            Set<Integer> anos = new HashSet<>();
            for (int a = hoy.getYear(); a <= limite.getYear(); a++) {
                anos.add(a);
            }

            DeadlineView v = evaluador.evaluar(limite, hoy, new CalendarSnapshot(feriados, anos));

            assertThat(v.diasHabiles())
                    .as("hoy=%s limite=%s feriados=%d", hoy, limite, feriados.size())
                    .isEqualTo(contarADedo(hoy, limite, feriados));
        }
    }

    @Test
    @DisplayName("el conteo nunca es negativo ni supera los dias naturales del intervalo")
    void invariantes() {
        Random azar = new Random(7L);
        for (int caso = 0; caso < 200; caso++) {
            LocalDate hoy = LocalDate.of(2026, 3, 1).plusDays(azar.nextInt(365));
            LocalDate limite = hoy.plusDays(1 + azar.nextInt(90));
            // Set.of revienta con duplicados: si ambas fechas caen en el mismo ano.
            Set<Integer> anos = new HashSet<>(java.util.List.of(hoy.getYear(), limite.getYear()));

            var v = evaluador.evaluar(limite, hoy, new CalendarSnapshot(Set.of(), anos));
            long naturales = java.time.temporal.ChronoUnit.DAYS.between(hoy, limite);

            assertThat(v.diasHabiles()).isNotNegative().isLessThanOrEqualTo((int) naturales);
        }
    }

    @Test
    @DisplayName("anadir un feriado nunca aumenta los dias habiles")
    void monotonia() {
        LocalDate hoy = LocalDate.of(2026, 9, 4);
        LocalDate limite = LocalDate.of(2026, 12, 31);
        Set<Integer> anos = Set.of(2026);

        int sinFeriados = evaluador.evaluar(limite, hoy,
                new CalendarSnapshot(Set.of(), anos)).diasHabiles();

        Set<LocalDate> feriados = new HashSet<>();
        for (LocalDate d = hoy; !d.isAfter(limite); d = d.plusDays(7)) {
            feriados.add(d);
            int conFeriados = evaluador.evaluar(limite, hoy,
                    new CalendarSnapshot(feriados, anos)).diasHabiles();
            assertThat(conFeriados).isLessThanOrEqualTo(sinFeriados);
        }
    }
}
