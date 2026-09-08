package pe.org.beneficencia.legalcontrol.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;

/**
 * Los feriados de ley, comprobados contra fechas conocidas.
 *
 * <p>La parte delicada es la Pascua: las catorce fechas fijas se pueden leer, pero
 * Jueves y Viernes Santo salen de un algoritmo, y un algoritmo mal transcrito
 * produce fechas verosimiles y equivocadas. Por eso se anclan a anos publicados.
 */
class FeriadosDelPeruTest {

    // ------------------------------------------------------------------- Pascua

    @Test
    @DisplayName("la Pascua cae donde dicen las fuentes publicadas")
    void pascuaEnAnosConocidos() {
        assertThat(FeriadosDelPeru.domingoDePascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(FeriadosDelPeru.domingoDePascua(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(FeriadosDelPeru.domingoDePascua(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(FeriadosDelPeru.domingoDePascua(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
    }

    @Test
    @DisplayName("la Pascua siempre es domingo, ano tras ano")
    void pascuaSiempreEnDomingo() {
        // Una transcripcion con un signo cambiado sigue dando fechas de primavera,
        // pero deja de caer en domingo. Comprobarlo en un siglo lo delata.
        for (int ano = 2000; ano <= 2100; ano++) {
            assertThat(FeriadosDelPeru.domingoDePascua(ano).getDayOfWeek())
                    .as("Pascua de %d", ano)
                    .isEqualTo(DayOfWeek.SUNDAY);
        }
    }

    @Test
    @DisplayName("Jueves y Viernes Santo son los dos dias anteriores a la Pascua")
    void semanaSanta2026() {
        var feriados = FeriadosDelPeru.delAno(2026, false);

        assertThat(feriados).extracting(FeriadosDelPeru.Feriado::dia)
                .contains(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3));
        assertThat(feriados).filteredOn(f -> f.descripcion().equals("Jueves Santo"))
                .singleElement()
                .extracting(f -> f.dia().getDayOfWeek())
                .isEqualTo(DayOfWeek.THURSDAY);
    }

    // ------------------------------------------------------------------- la lista

    @Test
    @DisplayName("son dieciseis feriados nacionales al ano")
    void dieciseisNacionales() {
        // Los catorce de ley mas Jueves y Viernes Santo: la cifra que publican las
        // fuentes para 2026 y la que resulta del DL 713 con la Ley 31068.
        assertThat(FeriadosDelPeru.delAno(2026, false)).hasSize(16);
        assertThat(FeriadosDelPeru.delAno(2027, false)).hasSize(16);
    }

    @Test
    @DisplayName("el 15 de agosto es regional, no nacional")
    void arequipaEsRegional() {
        var conArequipa = FeriadosDelPeru.delAno(2026, true);

        assertThat(conArequipa).hasSize(17);
        assertThat(conArequipa)
                .filteredOn(f -> f.dia().equals(LocalDate.of(2026, 8, 15)))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.tipo())
                            .as("solo rige en la provincia de Arequipa, por Ley 24875")
                            .isEqualTo(Tipo.REGIONAL_HOLIDAY);
                    assertThat(f.descripcion())
                            .as("la jefa debe poder verificar la norma sin preguntar")
                            .contains("24875");
                });
        assertThat(conArequipa).filteredOn(f -> f.tipo() == Tipo.NATIONAL_HOLIDAY).hasSize(16);
    }

    @Test
    @DisplayName("las fechas de ley estan todas y ninguna se repite")
    void fechasFijasDeLey() {
        var dias = FeriadosDelPeru.delAno(2026, true).stream()
                .map(FeriadosDelPeru.Feriado::dia).toList();

        assertThat(dias).doesNotHaveDuplicates();
        assertThat(dias).containsSequence(
                LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 29));
        assertThat(dias).contains(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 10, 8), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 8),
                LocalDate.of(2026, 12, 9), LocalDate.of(2026, 12, 25));
    }

    @Test
    @DisplayName("no hay fechas repetidas en ningun ano, ni cuando la Pascua es tardia")
    void sinChoquesEnNingunAno() {
        // La Pascua se mueve entre el 22 de marzo y el 25 de abril: nunca choca con
        // una fecha fija, pero conviene que sea el codigo quien lo demuestre.
        for (int ano = 2020; ano <= 2060; ano++) {
            assertThat(FeriadosDelPeru.delAno(ano, true))
                    .as("feriados de %d", ano)
                    .extracting(FeriadosDelPeru.Feriado::dia)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("todos los dias pertenecen al ano pedido")
    void todosDelAnoPedido() {
        for (int ano : new int[] {2025, 2026, 2027, 2038}) {
            assertThat(FeriadosDelPeru.delAno(ano, true))
                    .allSatisfy(f -> assertThat(f.dia().getYear()).isEqualTo(ano));
        }
    }

    @Test
    @DisplayName("la lista viene ordenada por fecha")
    void ordenadaPorFecha() {
        var dias = FeriadosDelPeru.delAno(2027, true).stream()
                .map(FeriadosDelPeru.Feriado::dia).toList();

        assertThat(dias).isSorted();
    }

    @Test
    @DisplayName("las descripciones caben en la columna y llevan sus tildes")
    void descripcionesValidas() {
        assertThat(FeriadosDelPeru.delAno(2026, true)).allSatisfy(f -> {
            assertThat(f.descripcion()).isNotBlank();
            // La restriccion de la tabla: char_length(description) <= 150.
            assertThat(f.descripcion().length()).isLessThanOrEqualTo(150);
        });
        assertThat(FeriadosDelPeru.delAno(2026, false))
                .extracting(FeriadosDelPeru.Feriado::descripcion)
                .contains("Año Nuevo", "Día del Trabajo", "Batalla de Junín",
                        "Inmaculada Concepción");
    }
}
