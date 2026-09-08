package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.team.SemanaDeTrabajo;

/**
 * Los limites de la semana, con <b>fechas absolutas</b>.
 *
 * <p>Una prueba escrita con {@code LocalDate.now()} pasaria seis dias de cada siete
 * y fallaria el domingo, o el ultimo lunes del ano. Las fechas van fijas para que
 * el caso raro se compruebe siempre, no cuando toque.
 */
class SemanaDeTrabajoTest {

    @Test
    @DisplayName("desde un miercoles, la semana va de su lunes a su domingo")
    void semanaDesdeElMedio() {
        LocalDate miercoles = LocalDate.of(2026, 9, 9);
        assertThat(miercoles.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);

        assertThat(SemanaDeTrabajo.lunesDe(miercoles)).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(SemanaDeTrabajo.domingoDe(miercoles)).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("un lunes es el lunes de su propia semana, no del anterior")
    void elLunesEsSuPropioLunes() {
        LocalDate lunes = LocalDate.of(2026, 9, 7);

        assertThat(SemanaDeTrabajo.lunesDe(lunes)).isEqualTo(lunes);
        assertThat(SemanaDeTrabajo.domingoDe(lunes)).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("el domingo cierra su semana; no abre la siguiente")
    void elDomingoCierraSuSemana() {
        LocalDate domingo = LocalDate.of(2026, 9, 13);
        assertThat(domingo.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

        // Con la convencion contraria, un plazo del domingo saldria en la carga de
        // la semana siguiente y desapareceria de la que de verdad lo tiene encima.
        assertThat(SemanaDeTrabajo.lunesDe(domingo)).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(SemanaDeTrabajo.domingoDe(domingo)).isEqualTo(domingo);
    }

    @Test
    @DisplayName("la semana que cruza el cambio de ano no se parte")
    void semanaACaballoEntreDosAnos() {
        // El 1 de enero de 2027 es viernes: su semana empieza en 2026.
        LocalDate anoNuevo = LocalDate.of(2027, 1, 1);
        assertThat(anoNuevo.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);

        assertThat(SemanaDeTrabajo.lunesDe(anoNuevo)).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(SemanaDeTrabajo.domingoDe(anoNuevo)).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    @DisplayName("todos los dias de una misma semana dan los mismos limites")
    void todosLosDiasDanLoMismo() {
        LocalDate lunes = LocalDate.of(2026, 9, 7);

        for (int i = 0; i < 7; i++) {
            LocalDate dia = lunes.plusDays(i);
            assertThat(SemanaDeTrabajo.lunesDe(dia)).as("lunes de %s", dia).isEqualTo(lunes);
            assertThat(SemanaDeTrabajo.domingoDe(dia)).as("domingo de %s", dia)
                    .isEqualTo(lunes.plusDays(6));
        }
    }

    @Test
    @DisplayName("el domingo siempre va seis dias despues del lunes, en cualquier fecha")
    void invarianteEnUnAnoEntero() {
        for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2028, 1, 1));
                d = d.plusDays(1)) {
            LocalDate lunes = SemanaDeTrabajo.lunesDe(d);
            assertThat(lunes.getDayOfWeek()).as("%s", d).isEqualTo(DayOfWeek.MONDAY);
            assertThat(SemanaDeTrabajo.domingoDe(d)).isEqualTo(lunes.plusDays(6));
            assertThat(!d.isBefore(lunes) && !d.isAfter(lunes.plusDays(6)))
                    .as("%s tiene que caer dentro de su propia semana", d).isTrue();
        }
    }
}
