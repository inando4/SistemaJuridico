package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La zona horaria se nombra en un solo sitio.
 *
 * <p>{@code ClockConfig.ZONA} existe desde la 001, pero dos sitios volvieron a
 * escribir {@code ZoneId.of("America/Lima")} a mano. Mientras el valor coincida no
 * se nota; el problema es que la 006 anade varias fronteras de dia —el corte de
 * «que hice hoy», el ancla del calendario— y cada literal suelto es una de esas
 * fronteras que puede quedarse atras si algun dia la zona cambia o si una prueba
 * necesita fijarla.
 *
 * <p>Se comprueba sobre las fuentes y no sobre el comportamiento porque es
 * precisamente lo que ninguna prueba de comportamiento ve: dos literales iguales
 * dan el mismo resultado hasta el dia en que dejan de darlo.
 */
class ZonaHorariaCentralizadaTest {

    private static final Path FUENTES =
            Path.of("src/main/java/pe/org/beneficencia/legalcontrol");

    /** El unico archivo donde la zona puede escribirse literalmente. */
    private static final String DECLARACION = "config/ClockConfig.java";

    private static final String LITERAL = "ZoneId.of(\"America/Lima\")";

    @Test
    @DisplayName("solo ClockConfig nombra la zona horaria")
    void laZonaSeDeclaraUnaSolaVez() throws IOException {
        List<String> infractores = new ArrayList<>();

        try (Stream<Path> archivos = Files.walk(FUENTES)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (archivo.toString().replace('\\', '/').endsWith(DECLARACION)) {
                    continue;
                }
                if (Files.readString(archivo).contains(LITERAL)) {
                    infractores.add(FUENTES.relativize(archivo).toString());
                }
            }
        }

        assertThat(infractores)
                .as("estos archivos repiten la zona horaria en vez de usar ClockConfig.ZONA")
                .isEmpty();
    }

    @Test
    @DisplayName("ClockConfig sigue declarando la zona")
    void laDeclaracionSigueEnSuSitio() throws IOException {
        String clockConfig = Files.readString(FUENTES.resolve(DECLARACION));

        assertThat(clockConfig)
                .as("si la declaracion se mueve, la prueba anterior pasaria por vacia")
                .contains(LITERAL);
    }
}
