package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;

/**
 * Antiguedad de un pendiente sin fecha limite (insumo, seccion 19).
 *
 * <p>Los bordes exactos importan: el insumo dice «mas de quince días hábiles», de
 * modo que quince no avisa y dieciseis si. Un error de uno aqui significa avisar un
 * dia antes o un dia tarde, todos los dias.
 */
class AntiguedadSinPlazoTest {

    private final DeadlineEvaluator evaluador = new DeadlineEvaluator();

    // Lunes 3 de agosto de 2026.
    private static final LocalDate RECEPCION = LocalDate.of(2026, 8, 3);

    private CalendarSnapshot sinFeriados() {
        return new CalendarSnapshot(Set.of(), Set.of(2026, 2027));
    }

    private int antiguedadAl(LocalDate hoy) {
        return evaluador.diasHabilesTranscurridos(RECEPCION, hoy, sinFeriados()).orElseThrow();
    }

    @Test
    @DisplayName("el mismo dia de recepcion son cero días hábiles")
    void mismoDia() {
        assertThat(antiguedadAl(RECEPCION)).isZero();
    }

    @Test
    @DisplayName("cuenta solo los días hábiles, saltando los fines de semana")
    void saltaFinesDeSemana() {
        // Del lunes 3 al lunes 10: cinco habiles de esa semana mas el lunes.
        assertThat(antiguedadAl(LocalDate.of(2026, 8, 10))).isEqualTo(5);
    }

    @Test
    @DisplayName("a los quince días hábiles todavia NO se avisa")
    void quinceNoAvisa() {
        // El vigesimo primer dia natural es el vigesimo cuarto de agosto: 15 habiles.
        LocalDate hoy = LocalDate.of(2026, 8, 24);
        int antiguedad = antiguedadAl(hoy);

        assertThat(antiguedad).isEqualTo(15);
        assertThat(antiguedad > DeadlineEvaluator.UMBRAL_SIN_PLAZO)
                .as("el insumo dice «mas de quince»: quince aun no").isFalse();
    }

    @Test
    @DisplayName("a los dieciseis días hábiles si se avisa")
    void dieciseisAvisa() {
        LocalDate hoy = LocalDate.of(2026, 8, 25);
        int antiguedad = antiguedadAl(hoy);

        assertThat(antiguedad).isEqualTo(16);
        assertThat(antiguedad > DeadlineEvaluator.UMBRAL_SIN_PLAZO).isTrue();
    }

    @Test
    @DisplayName("un feriado en medio retrasa el aviso un dia")
    void feriadoRetrasaElAviso() {
        var conFeriado = new CalendarSnapshot(
                Set.of(LocalDate.of(2026, 8, 12)), Set.of(2026, 2027));

        int antiguedad = evaluador.diasHabilesTranscurridos(
                RECEPCION, LocalDate.of(2026, 8, 25), conFeriado).orElseThrow();

        assertThat(antiguedad).as("un dia habil menos que sin feriado").isEqualTo(15);
    }

    @Test
    @DisplayName("sin cobertura de calendario no se calcula antiguedad")
    void sinCoberturaNoCalcula() {
        var vacio = new CalendarSnapshot(Set.of(), Set.of());

        assertThat(evaluador.diasHabilesTranscurridos(
                RECEPCION, LocalDate.of(2026, 8, 25), vacio))
                .as("una antiguedad sobre dias sin revisar parece un dato y no lo es")
                .isEmpty();
    }

    @Test
    @DisplayName("sin fecha de recepcion no se inventa una antiguedad")
    void sinRecepcionNoCalcula() {
        assertThat(evaluador.diasHabilesTranscurridos(
                null, LocalDate.of(2026, 8, 25), sinFeriados())).isEmpty();
    }

    @Test
    @DisplayName("una recepcion futura no da antiguedad negativa")
    void recepcionFutura() {
        assertThat(evaluador.diasHabilesTranscurridos(
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 8, 25), sinFeriados()))
                .isEmpty();
    }
}
