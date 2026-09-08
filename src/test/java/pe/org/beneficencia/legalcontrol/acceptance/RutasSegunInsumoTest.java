package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las rutas visibles coinciden con las que fija el insumo del cliente.
 *
 * <p>Esta comprobacion existe porque la desviacion ya ocurrio: la funcionalidad 001
 * se construyo con {@code /judicial-cases} cuando el insumo dice {@code /judiciales},
 * y nadie lo noto hasta meses despues. Una ruta es texto que el usuario ve en la
 * barra de direcciones, asi que le aplica el principio I igual que a cualquier
 * etiqueta.
 */
class RutasSegunInsumoTest {

    private static final Path CONTROLADORES =
            Path.of("src/main/java/pe/org/beneficencia/legalcontrol");

    private static final Pattern MAPEO =
            Pattern.compile("@(?:Get|Post)Mapping\\(\"([^\"]+)\"");

    /**
     * Rutas que el insumo fija literalmente. Estan reservadas: una funcionalidad
     * futura no debe ocupar la ruta de otra.
     */
    private static final List<String> FIJADAS_POR_EL_INSUMO = List.of(
            "/judiciales", "/administrativos", "/pendientes", "/pendientes/hoy",
            "/cumplidos", "/calendario", "/alertas", "/configuracion", "/actividad-diaria");

    /** Palabras en ingles que delatarian una ruta sin traducir. */
    private static final Pattern INGLES = Pattern.compile(
            "/(cases|users|days|statuses|new|edit|delete|history|review|reset|"
            + "deactivate|reactivate|reissue|availability|visibility|redeem|password|account)\\b");

    private List<String> rutas() throws IOException {
        List<String> encontradas = new ArrayList<>();
        try (Stream<Path> fuentes = Files.walk(CONTROLADORES)) {
            for (Path fuente : fuentes.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = MAPEO.matcher(Files.readString(fuente));
                while (m.find()) {
                    encontradas.add(m.group(1));
                }
            }
        }
        return encontradas;
    }

    @Test
    @DisplayName("ninguna ruta visible lleva palabras en ingles")
    void rutasEnEspanol() throws IOException {
        List<String> enIngles = rutas().stream()
                // /login y /logout son convencion del marco de seguridad y se entienden
                // universalmente; cambiarlas tocaria la configuracion de autenticacion
                // sin ganancia para quien las lee.
                .filter(r -> !r.startsWith("/login") && !r.startsWith("/logout"))
                .filter(r -> INGLES.matcher(r).find())
                .toList();

        assertThat(enIngles)
                .as("una ruta es texto que el usuario ve; le aplica el principio I")
                .isEmpty();
    }

    @Test
    @DisplayName("los expedientes judiciales viven en la ruta que fija el insumo")
    void rutaJudicialSegunInsumo() throws IOException {
        List<String> encontradas = rutas();

        assertThat(encontradas).contains("/judiciales", "/judiciales/{id}");
        assertThat(encontradas).noneMatch(r -> r.contains("judicial-cases"));
    }

    @Test
    @DisplayName("los procedimientos administrativos viven en la ruta que fija el insumo")
    void rutaAdministrativaSegunInsumo() throws IOException {
        List<String> encontradas = rutas();

        assertThat(encontradas).contains("/administrativos", "/administrativos/{id}");
        assertThat(encontradas).noneMatch(r -> r.contains("administrative-procedure"));
    }

    @Test
    @DisplayName("ninguna pantalla ocupa una ruta reservada para otra funcionalidad")
    void sinInvadirRutasReservadas() throws IOException {
        List<String> encontradas = rutas();

        // /calendario es la vista de calendario con pendientes y audiencias (insumo 31),
        // no la administracion de dias no laborables. Ocuparla ahora dejaria sin sitio a
        // esa pantalla cuando se construya.
        assertThat(encontradas)
                .as("/calendario pertenece a la vista de calendario, no a los feriados")
                .doesNotContain("/calendario");

        // /pendientes y /cumplidos dejaron de estar reservadas con la funcionalidad
        // 003; / y /alertas, con la 004. Estas dos siguen esperando la suya:
        // «que hice hoy» (seccion 33) y configuracion (36).
        for (String reservada : List.of("/actividad-diaria", "/configuracion")) {
            assertThat(encontradas)
                    .as("%s esta reservada por el insumo para una funcionalidad futura", reservada)
                    .doesNotContain(reservada);
        }
    }

    @Test
    @DisplayName("el panel y las alertas viven donde fija el insumo")
    void rutasDelPanelSegunInsumo() throws IOException {
        List<String> encontradas = rutas();

        assertThat(encontradas)
                .as("el panel es la pantalla de entrada (seccion 23)")
                .contains("/");
        assertThat(encontradas)
                .as("la seccion 35 fija esta ruta literalmente")
                .contains("/alertas");
    }

    @Test
    @DisplayName("los pendientes viven en las rutas que fija el insumo")
    void rutasDePendientesSegunInsumo() throws IOException {
        List<String> encontradas = rutas();

        assertThat(encontradas).contains("/pendientes", "/pendientes/{id}",
                "/pendientes/hoy", "/cumplidos");
        assertThat(encontradas).noneMatch(r -> r.contains("pending-task"));
    }

    @Test
    @DisplayName("el insumo reserva las rutas que este proyecto debe respetar")
    void inventarioDeRutasReservadas() {
        // Deja constancia de la lista para quien construya las siguientes fases.
        assertThat(FIJADAS_POR_EL_INSUMO).hasSize(9);
    }
}
