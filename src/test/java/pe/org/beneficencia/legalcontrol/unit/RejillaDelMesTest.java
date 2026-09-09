package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.agenda.RejillaDelMes;

/**
 * Los rangos de las tres vistas y el reparto en semanas.
 *
 * <p>Es aritmetica de calendario, no de dias habiles: nada de esto depende de que el
 * año este revisado. Los casos que importan son los bordes —un mes que empieza en
 * domingo, otro que acaba en lunes, y una rejilla que cruza el cambio de año—, porque
 * son los que dejan una rejilla mal cerrada.
 */
class RejillaDelMesTest {

    @Test
    @DisplayName("la vista de dia abarca un solo dia")
    void vistaDeDia() {
        LocalDate ancla = LocalDate.of(2026, 9, 8);

        assertThat(RejillaDelMes.desde("dia", ancla)).isEqualTo(ancla);
        assertThat(RejillaDelMes.hasta("dia", ancla)).isEqualTo(ancla);
        assertThat(RejillaDelMes.dias(ancla, ancla)).containsExactly(ancla);
    }

    @Test
    @DisplayName("la vista de semana va de lunes a domingo")
    void vistaDeSemana() {
        // 2026-09-08 es martes.
        LocalDate martes = LocalDate.of(2026, 9, 8);

        assertThat(RejillaDelMes.desde("semana", martes)).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(RejillaDelMes.hasta("semana", martes)).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(RejillaDelMes.dias(RejillaDelMes.desde("semana", martes),
                RejillaDelMes.hasta("semana", martes))).hasSize(7);
    }

    @Test
    @DisplayName("la rejilla del mes empieza en lunes y acaba en domingo")
    void rejillaCerradaEnSemanasCompletas() {
        LocalDate enSeptiembre = LocalDate.of(2026, 9, 15);

        LocalDate desde = RejillaDelMes.desde("mes", enSeptiembre);
        LocalDate hasta = RejillaDelMes.hasta("mes", enSeptiembre);

        assertThat(desde.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(hasta.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(desde).isBeforeOrEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(hasta).isAfterOrEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("un mes que empieza en jueves incluye dias del mes anterior")
    void mesQueEmpiezaEntreSemana() {
        // Octubre de 2026 empieza en jueves.
        LocalDate desde = RejillaDelMes.desde("mes", LocalDate.of(2026, 10, 10));

        assertThat(desde).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(desde.getMonthValue())
                .as("esos dias vecinos se pintan, asi que se piden en el mismo viaje")
                .isEqualTo(9);
    }

    @Test
    @DisplayName("un mes que empieza en lunes no arrastra nada por delante")
    void mesQueEmpiezaEnLunes() {
        // Junio de 2026 empieza en lunes.
        assertThat(RejillaDelMes.desde("mes", LocalDate.of(2026, 6, 15)))
                .isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("la rejilla de diciembre cruza el cambio de año")
    void rejillaQueCruzaElAno() {
        LocalDate desde = RejillaDelMes.desde("mes", LocalDate.of(2026, 12, 15));
        LocalDate hasta = RejillaDelMes.hasta("mes", LocalDate.of(2026, 12, 15));

        assertThat(hasta.getYear())
                .as("es el caso que obliga a pedir el calendario por el rango y no por hoy")
                .isEqualTo(2027);
        assertThat(desde.getYear()).isEqualTo(2026);
    }

    @Test
    @DisplayName("las semanas salen en filas de siete")
    void filasDeSiete() {
        LocalDate desde = RejillaDelMes.desde("mes", LocalDate.of(2026, 9, 15));
        LocalDate hasta = RejillaDelMes.hasta("mes", LocalDate.of(2026, 9, 15));

        var semanas = RejillaDelMes.semanas(desde, hasta);

        assertThat(semanas).isNotEmpty();
        assertThat(semanas).allSatisfy(fila -> assertThat(fila).hasSize(7));
    }

    @Test
    @DisplayName("una vista desconocida cae en mes, no revienta")
    void vistaDesconocida() {
        assertThat(RejillaDelMes.vistaValida("inventada")).isEqualTo("mes");
        assertThat(RejillaDelMes.vistaValida(null)).isEqualTo("mes");
    }

    @Test
    @DisplayName("la navegacion avanza por el periodo de su vista")
    void navegacionPorPeriodo() {
        LocalDate ancla = LocalDate.of(2026, 9, 8);

        assertThat(RejillaDelMes.siguiente("dia", ancla)).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(RejillaDelMes.siguiente("semana", ancla)).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(RejillaDelMes.siguiente("mes", ancla)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(RejillaDelMes.anterior("mes", ancla)).isEqualTo(LocalDate.of(2026, 8, 8));
    }
}
