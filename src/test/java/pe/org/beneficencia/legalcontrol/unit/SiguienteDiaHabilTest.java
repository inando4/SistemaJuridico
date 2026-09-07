package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;

/**
 * El siguiente dia habil, que usa la accion «no cumpli» para reprogramar sola.
 *
 * <p>La comprobacion que mas importa es la ultima: sin cobertura de calendario la
 * funcion NO devuelve fecha. Reprogramar a un dia que resulto ser feriado es peor
 * que no reprogramar, porque nadie se entera hasta el vencimiento.
 */
class SiguienteDiaHabilTest {

    private final DeadlineEvaluator evaluador = new DeadlineEvaluator();

    private CalendarSnapshot calendario(Set<LocalDate> feriados, Integer... anos) {
        return new CalendarSnapshot(feriados, Set.of(anos));
    }

    @Test
    @DisplayName("de viernes pasa al lunes")
    void viernesALunes() {
        // Viernes 4 de septiembre de 2026.
        var siguiente = evaluador.siguienteDiaHabil(
                LocalDate.of(2026, 9, 4), calendario(Set.of(), 2026, 2027));

        assertThat(siguiente).contains(LocalDate.of(2026, 9, 7));
    }

    @Test
    @DisplayName("de un dia entre semana pasa al siguiente")
    void entreSemana() {
        var siguiente = evaluador.siguienteDiaHabil(
                LocalDate.of(2026, 9, 8), calendario(Set.of(), 2026, 2027));

        assertThat(siguiente).contains(LocalDate.of(2026, 9, 9));
    }

    @Test
    @DisplayName("salta un feriado registrado")
    void saltaFeriado() {
        var conFeriado = calendario(Set.of(LocalDate.of(2026, 9, 7)), 2026, 2027);

        var siguiente = evaluador.siguienteDiaHabil(LocalDate.of(2026, 9, 4), conFeriado);

        assertThat(siguiente).as("el lunes es feriado, pasa al martes")
                .contains(LocalDate.of(2026, 9, 8));
    }

    @Test
    @DisplayName("salta una cadena de varios feriados seguidos")
    void saltaVariosFeriados() {
        var conPuente = calendario(Set.of(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9)), 2026, 2027);

        var siguiente = evaluador.siguienteDiaHabil(LocalDate.of(2026, 9, 4), conPuente);

        assertThat(siguiente).contains(LocalDate.of(2026, 9, 10));
    }

    @Test
    @DisplayName("cruza el fin de ano si hace falta")
    void cruzaElAno() {
        // Jueves 31 de diciembre de 2026; el 1 de enero es feriado.
        var conAnoNuevo = calendario(Set.of(LocalDate.of(2027, 1, 1)), 2026, 2027);

        var siguiente = evaluador.siguienteDiaHabil(LocalDate.of(2026, 12, 31), conAnoNuevo);

        assertThat(siguiente).as("viernes feriado, sabado y domingo: pasa al lunes")
                .contains(LocalDate.of(2027, 1, 4));
    }

    @Test
    @DisplayName("sin cobertura del ano no devuelve fecha")
    void sinCoberturaNoDevuelveFecha() {
        var soloEsteAno = calendario(Set.of(), 2026);

        var siguiente = evaluador.siguienteDiaHabil(LocalDate.of(2026, 12, 31), soloEsteAno);

        assertThat(siguiente).as("el dia siguiente cae en 2027, sin revisar").isEmpty();
    }

    @Test
    @DisplayName("sin ningun ano cubierto tampoco devuelve fecha")
    void sinCalendarioNoDevuelveFecha() {
        var vacio = calendario(Set.of());

        assertThat(evaluador.siguienteDiaHabil(LocalDate.of(2026, 9, 4), vacio)).isEmpty();
    }

    @Test
    @DisplayName("una fecha nula no revienta")
    void fechaNula() {
        assertThat(evaluador.siguienteDiaHabil(null, calendario(Set.of(), 2026))).isEmpty();
    }
}
