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
 * La interfaz esta en espanol y ningun aviso depende solo del color (FR-021).
 *
 * <p>Se comprueba sobre las plantillas, no sobre una pantalla concreta: asi cubre
 * tambien las que nadie recuerde visitar en una revision manual.
 */
class InterfazEnEspanolTest {

    private static final Path PLANTILLAS = Path.of("src/main/resources/templates");

    /** Palabras que delatarian una etiqueta sin traducir. */
    private static final Pattern INGLES = Pattern.compile(
            ">\\s*(Save|Cancel|Submit|Delete|Edit|Search|Login|Logout|Username|Password|"
            + "Name|Email|Date|Status|Back|Next|Previous|Yes|No)\\s*<");

    /**
     * Un elemento marcado como error debe llevar texto, no solo un color.
     *
     * <p>Se captura el contenido completo hasta la etiqueta de cierre, porque el
     * texto puede venir dentro de un {@code <strong>} u otro elemento anidado.
     */
    private static final Pattern AVISO = Pattern.compile(
            "<(p|span|div)([^>]*class=\"[^\"]*(?:error|vencido|vence-hoy)[^\"]*\"[^>]*)>(.*?)</\\1>",
            Pattern.DOTALL);

    private List<Path> plantillas() throws IOException {
        try (Stream<Path> archivos = Files.walk(PLANTILLAS)) {
            return archivos.filter(p -> p.toString().endsWith(".html")).toList();
        }
    }

    @Test
    @DisplayName("ninguna plantilla tiene etiquetas en ingles")
    void sinEtiquetasEnIngles() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (Path plantilla : plantillas()) {
            Matcher m = INGLES.matcher(Files.readString(plantilla));
            while (m.find()) {
                hallazgos.add(plantilla.getFileName() + ": " + m.group().trim());
            }
        }
        assertThat(hallazgos).isEmpty();
    }

    @Test
    @DisplayName("todo aviso lleva texto: el color nunca es la unica senal")
    void avisosConTexto() throws IOException {
        List<String> mudos = new ArrayList<>();
        for (Path plantilla : plantillas()) {
            String html = Files.readString(plantilla);
            Matcher m = AVISO.matcher(html);
            while (m.find()) {
                // Se retiran las etiquetas anidadas y queda solo el texto visible.
                String contenido = m.group(3).replaceAll("<[^>]*>", " ");
                boolean tieneTexto = !contenido.isBlank();
                // th:text tambien cuenta: el texto lo pone el servidor.
                boolean tieneThText = m.group().contains("th:text");
                if (!tieneTexto && !tieneThText) {
                    mudos.add(plantilla.getFileName() + ": " + m.group().replaceAll("\\s+", " "));
                }
            }
        }
        assertThat(mudos).as("un aviso solo con color es invisible para quien no lo distingue")
                .isEmpty();
    }

    @Test
    @DisplayName("cada campo de formulario tiene su etiqueta asociada")
    void camposConEtiqueta() throws IOException {
        Pattern campo = Pattern.compile("<(input|select|textarea)[^>]*\\sid=\"([^\"]+)\"");
        List<String> huerfanos = new ArrayList<>();

        for (Path plantilla : plantillas()) {
            String html = Files.readString(plantilla);
            Matcher m = campo.matcher(html);
            while (m.find()) {
                String id = m.group(2);
                if (!html.contains("for=\"" + id + "\"")) {
                    huerfanos.add(plantilla.getFileName() + ": #" + id);
                }
            }
        }
        assertThat(huerfanos).as("sin label, un lector de pantalla no dice que se pide")
                .isEmpty();
    }

    @Test
    @DisplayName("las paginas declaran el idioma espanol")
    void idiomaDeclarado() throws IOException {
        for (Path plantilla : plantillas()) {
            assertThat(Files.readString(plantilla))
                    .as("%s debe declarar lang=es", plantilla.getFileName())
                    .contains("lang=\"es\"");
        }
    }
}
