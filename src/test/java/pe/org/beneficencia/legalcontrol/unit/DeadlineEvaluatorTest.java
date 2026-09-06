package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;

/** Los doce casos de la spec, con fechas reales y verificables a mano. */
class DeadlineEvaluatorTest {

    private final DeadlineEvaluator evaluador = new DeadlineEvaluator();

    // Viernes 4 de septiembre de 2026.
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 4);

    private CalendarSnapshot calendario(Set<LocalDate> feriados, Integer... anos) {
        return new CalendarSnapshot(feriados, Set.of(anos));
    }

    private CalendarSnapshot sinFeriados() {
        return calendario(Set.of(), 2026, 2027);
    }

    @Test
    @DisplayName("sin fecha limite no hay plazo que interpretar")
    void sinFecha() {
        var v = evaluador.evaluar(null, VIERNES, sinFeriados());
        assertThat(v.estado()).isEqualTo(DeadlineView.Estado.SIN_FECHA);
        assertThat(v.texto()).isEqualTo("Sin fecha limite");
    }

    @Test
    @DisplayName("una fecha anterior a hoy esta vencida")
    void vencido() {
        var v = evaluador.evaluar(VIERNES.minusDays(1), VIERNES, sinFeriados());
        assertThat(v.estado()).isEqualTo(DeadlineView.Estado.VENCIDO);
        assertThat(v.texto()).isEqualTo("Vencido");
    }

    @Test
    @DisplayName("la fecha de hoy vence hoy")
    void venceHoy() {
        var v = evaluador.evaluar(VIERNES, VIERNES, sinFeriados());
        assertThat(v.estado()).isEqualTo(DeadlineView.Estado.VENCE_HOY);
    }

    @Test
    @DisplayName("del viernes al martes hay dos dias habiles: se excluye hoy y se salta el fin de semana")
    void cuentaExcluyendoHoyYFinDeSemana() {
        // sab 5 y dom 6 no cuentan; lun 7 y mar 8 si.
        var v = evaluador.evaluar(LocalDate.of(2026, 9, 8), VIERNES, sinFeriados());
        assertThat(v.diasHabiles()).isEqualTo(2);
        assertThat(v.texto()).isEqualTo("2 dias habiles restantes");
    }

    @Test
    @DisplayName("un feriado en medio descuenta un dia mas")
    void feriadoIntermedio() {
        var conFeriado = calendario(Set.of(LocalDate.of(2026, 9, 7)), 2026, 2027);
        var v = evaluador.evaluar(LocalDate.of(2026, 9, 8), VIERNES, conFeriado);
        assertThat(v.diasHabiles()).isEqualTo(1);
        assertThat(v.texto()).isEqualTo("1 dia habil restante");
    }

    @Test
    @DisplayName("el dia siguiente habil da exactamente uno")
    void unSoloDia() {
        // Lunes 7 desde el viernes 4.
        var v = evaluador.evaluar(LocalDate.of(2026, 9, 7), VIERNES, sinFeriados());
        assertThat(v.diasHabiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("un limite en sabado da cero dias habiles y se avisa, sin desplazarlo")
    void limiteEnSabado() {
        var v = evaluador.evaluar(LocalDate.of(2026, 9, 5), VIERNES, sinFeriados());
        assertThat(v.diasHabiles()).isZero();
        assertThat(v.limiteNoHabil()).isTrue();
        assertThat(v.estado()).as("no se convierte en vencido antes de tiempo")
                .isEqualTo(DeadlineView.Estado.PENDIENTE);
    }

    @Test
    @DisplayName("el limite se incluye en el conteo cuando es habil")
    void limiteIncluido() {
        // Del lunes 7 al martes 8: solo el martes.
        var v = evaluador.evaluar(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 7), sinFeriados());
        assertThat(v.diasHabiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("un plazo que cruza el ano exige cobertura de ambos anos")
    void cruceDeAno() {
        var soloEsteAno = calendario(Set.of(), 2026);
        var v = evaluador.evaluar(LocalDate.of(2027, 1, 15), LocalDate.of(2026, 12, 20), soloEsteAno);

        assertThat(v.estado()).isEqualTo(DeadlineView.Estado.SIN_CALENDARIO);
        assertThat(v.anosSinCobertura()).containsExactly(2027);
    }

    @Test
    @DisplayName("sin cobertura no se cuenta: se avisa y se nombran los anos que faltan")
    void sinCobertura() {
        var vacio = calendario(Set.of());
        var v = evaluador.evaluar(LocalDate.of(2026, 12, 31), VIERNES, vacio);

        assertThat(v.diasHabiles()).as("no se inventa un numero").isNull();
        assertThat(v.texto()).isEqualTo("Calculo no disponible: revisar dias no laborables");
        assertThat(v.anosFaltantes()).isEqualTo("2026");
    }

    @Test
    @DisplayName("vencido y vence hoy se conservan aunque falte cobertura")
    void comparacionesDeFechaSobrevivenSinCalendario() {
        var vacio = calendario(Set.of());

        assertThat(evaluador.evaluar(VIERNES.minusDays(3), VIERNES, vacio).estado())
                .isEqualTo(DeadlineView.Estado.VENCIDO);
        assertThat(evaluador.evaluar(VIERNES, VIERNES, vacio).estado())
                .isEqualTo(DeadlineView.Estado.VENCE_HOY);
    }

    @Test
    @DisplayName("un fin de semana completo por delante no suma dias habiles")
    void soloFinDeSemana() {
        // Del viernes 4 al domingo 6.
        var v = evaluador.evaluar(LocalDate.of(2026, 9, 6), VIERNES, sinFeriados());
        assertThat(v.diasHabiles()).isZero();
        assertThat(v.limiteNoHabil()).isTrue();
    }
}
