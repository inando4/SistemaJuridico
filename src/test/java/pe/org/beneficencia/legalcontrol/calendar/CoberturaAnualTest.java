package pe.org.beneficencia.legalcontrol.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cruce de anos y fechas frontera.
 *
 * <p><b>Fechas absolutas, no relativas a «hoy».</b> El fallo que motivo esta clase
 * —medir antiguedad con una instantanea que solo mira hacia adelante— solo se
 * manifiesta cuando la recepcion cae en el ano anterior, es decir, en enero. Una
 * prueba escrita con {@code LocalDate.now().minusDays(40)} pasa once meses al ano
 * y falla en el duodecimo.
 */
class CoberturaAnualTest {

    private final DeadlineEvaluator plazos = new DeadlineEvaluator();

    /** Sin dias no laborables: solo cuentan sabados y domingos. */
    private CalendarSnapshot cubriendo(Integer... anos) {
        return new CalendarSnapshot(Set.of(), Set.of(anos));
    }

    // ------------------------------------------------------- el fallo corregido

    @Test
    @DisplayName("una instantanea que solo mira hacia adelante no puede medir antiguedad")
    void haciaAdelanteNoSirveParaAntiguedad() {
        LocalDate hoy = LocalDate.of(2027, 1, 12);
        LocalDate recepcion = LocalDate.of(2026, 12, 15);

        // Lo que devolvia paraListado(hoy): del ano en curso hacia adelante.
        var resultado = plazos.diasHabilesTranscurridos(recepcion, hoy,
                cubriendo(2027, 2028, 2029, 2030, 2031, 2032));

        assertThat(resultado)
                .as("no puede contar 2026 porque no lo cubre; de ahi la falsa alarma")
                .isEmpty();
    }

    @Test
    @DisplayName("incluyendo el ano anterior, la antiguedad se mide y da un numero")
    void haciaAtrasSiMideAntiguedad() {
        LocalDate hoy = LocalDate.of(2027, 1, 12);
        LocalDate recepcion = LocalDate.of(2026, 12, 15);

        var resultado = plazos.diasHabilesTranscurridos(recepcion, hoy,
                cubriendo(2026, 2027, 2028, 2029, 2030, 2031, 2032));

        assertThat(resultado).isPresent();
        // Del 16 de diciembre al 12 de enero, descontando fines de semana.
        assertThat(resultado.get())
                .as("un pendiente de hace un mes supera el umbral de quince dias habiles")
                .isGreaterThan(DeadlineEvaluator.UMBRAL_SIN_PLAZO);
    }

    // ---------------------------------------------------------- sumarDiasHabiles

    @Test
    @DisplayName("el tercer dia habil desde un viernes es el miercoles siguiente")
    void tercerDiaHabilDesdeViernes() {
        LocalDate viernes = LocalDate.of(2027, 3, 5);

        var frontera = plazos.sumarDiasHabiles(viernes, 3, cubriendo(2027));

        // Lunes 8, martes 9, miercoles 10: el sabado y el domingo no cuentan.
        assertThat(frontera).contains(LocalDate.of(2027, 3, 10));
    }

    @Test
    @DisplayName("los dias no laborables tambien se saltan al buscar la frontera")
    void fronteraSaltaElFeriado() {
        LocalDate viernes = LocalDate.of(2027, 3, 5);
        LocalDate lunesNoLaborable = LocalDate.of(2027, 3, 8);
        var calendario = new CalendarSnapshot(Set.of(lunesNoLaborable), Set.of(2027));

        var frontera = plazos.sumarDiasHabiles(viernes, 3, calendario);

        // Martes 9, miercoles 10, jueves 11.
        assertThat(frontera).contains(LocalDate.of(2027, 3, 11));
    }

    @Test
    @DisplayName("sin cobertura no hay frontera: no se inventa una fecha")
    void sinCoberturaNoHayFrontera() {
        assertThat(plazos.sumarDiasHabiles(LocalDate.of(2027, 3, 5), 3, cubriendo(2031)))
                .isEmpty();
    }

    @Test
    @DisplayName("la frontera hacia adelante cruza el fin de ano")
    void fronteraCruzandoElAno() {
        LocalDate finDeAno = LocalDate.of(2026, 12, 30);

        assertThat(plazos.sumarDiasHabiles(finDeAno, 3, cubriendo(2026)))
                .as("sin cobertura de 2027 no puede cruzar")
                .isEmpty();
        assertThat(plazos.sumarDiasHabiles(finDeAno, 3, cubriendo(2026, 2027)))
                .isPresent();
    }

    // --------------------------------------------------------- restarDiasHabiles

    /**
     * La comprobacion que sostiene la coherencia entre el dashboard y la ficha.
     *
     * <p>La tarjeta compara recepciones contra una fecha frontera; la ficha cuenta
     * los dias habiles de cada pendiente. Si las dos formas no dieran el mismo
     * veredicto, la tarjeta contaria pendientes que su propio listado no muestra.
     */
    @Test
    @DisplayName("la frontera hacia atras dice lo mismo que contar dia a dia")
    void fronteraEquivaleAContar() {
        LocalDate hoy = LocalDate.of(2027, 3, 31);
        var calendario = cubriendo(2026, 2027);
        int umbral = DeadlineEvaluator.UMBRAL_SIN_PLAZO;

        LocalDate frontera = plazos.restarDiasHabiles(hoy, umbral, calendario).orElseThrow();

        for (LocalDate r = hoy.minusDays(60); !r.isAfter(hoy); r = r.plusDays(1)) {
            boolean superaContando =
                    plazos.diasHabilesTranscurridos(r, hoy, calendario).orElseThrow() > umbral;
            boolean superaPorFrontera = r.isBefore(frontera);

            assertThat(superaPorFrontera)
                    .as("recepcion %s: contando da %s, por frontera da %s", r,
                            superaContando, superaPorFrontera)
                    .isEqualTo(superaContando);
        }
    }

    @Test
    @DisplayName("la frontera hacia atras necesita el ano anterior cuando lo cruza")
    void fronteraHaciaAtrasCruzandoElAno() {
        LocalDate enero = LocalDate.of(2027, 1, 12);

        assertThat(plazos.restarDiasHabiles(enero, 15, cubriendo(2027)))
                .as("quince dias habiles atras desde el 12 de enero caen en 2026")
                .isEmpty();
        assertThat(plazos.restarDiasHabiles(enero, 15, cubriendo(2026, 2027)))
                .isPresent();
    }
}
