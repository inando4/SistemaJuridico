package pe.org.beneficencia.legalcontrol.shared;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditoriaUtilidadesTest {
    @Test
    void elDesplazamientoNoDesbordaInclusoConElMayorEntero() {
        assertThat(Paging.of(100_000_000).offset()).isEqualTo(2_500_000_000L);
        assertThat(Paging.of(Integer.MAX_VALUE).offset()).isEqualTo(53_687_091_175L);
        assertThat(Paging.of(-1).offset()).isZero();
    }

    @Test
    void conservaEspaciosTildesYComodinesCodificados() {
        String consulta = "?q=Revisi%C3%B3n+legal*&page=2";
        assertThat(VueltaAlListado.a("/pendientes", consulta)).isEqualTo("redirect:/pendientes" + consulta);
        assertThat(VueltaAlListado.ruta("/pendientes", consulta)).isEqualTo("/pendientes" + consulta);
        assertThat(VueltaAlListado.ficha("/pendientes/1", consulta))
                .isEqualTo("redirect:/pendientes/1?volver=%3Fq%3DRevisi%25C3%25B3n%2Blegal*%26page%3D2");
        assertThat(VueltaAlListado.ficha("/pendientes/1", "//sitio-ajeno.example"))
                .isEqualTo("redirect:/pendientes/1");
    }

    @Test
    void muestraElDiaLocalYNoElDiaUtcDelCumplimiento() {
        assertThat(new FechasDePantalla().fechaHora(Instant.parse("2026-09-27T04:58:09Z")))
                .isEqualTo("26/09/2026 23:58 (hora de Perú)");
        assertThat(new FechasDePantalla().fechaHora(null)).isEqualTo("—");
    }
}
