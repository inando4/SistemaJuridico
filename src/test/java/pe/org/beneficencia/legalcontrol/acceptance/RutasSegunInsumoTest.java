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

    /**
     * Reconoce {@code @GetMapping("/x")} y tambien {@code @GetMapping({"/x", "/y"})}.
     *
     * <p>El patron anterior exigia la comilla justo tras el parentesis, asi que las
     * anotaciones con varios valores no casaban <b>en absoluto</b>. Los cinco catalogos
     * —{@code /tipos-de-pendiente}, {@code /prioridades}, {@code /estados-de-pendiente},
     * {@code /estados-procesales} y {@code /estados-administrativos}— comparten una sola
     * anotacion de ese tipo, y llevaban desde la 003 sin que esta prueba los mirara: ni
     * el filtro de nombres en ingles ni las reservas de ruta los alcanzaban.
     */
    private static final Pattern MAPEO =
            Pattern.compile("@(?:Get|Post)Mapping\\(\\{?((?:\\s*\"[^\"]+\"\\s*,?)+)");

    private static final Pattern CADA_RUTA = Pattern.compile("\"([^\"]+)\"");

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
                    Matcher cada = CADA_RUTA.matcher(m.group(1));
                    while (cada.find()) {
                        encontradas.add(cada.group(1));
                    }
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

    /**
     * Sustituye a {@code sinInvadirRutasReservadas}.
     *
     * <p>Aquella comprobaba que nadie ocupara una ruta que el insumo tenia apartada
     * para una funcionalidad futura. Se fueron cumpliendo todas —{@code /pendientes} y
     * {@code /cumplidos} con la 003, {@code /} y {@code /alertas} con la 004,
     * {@code /actividad-diaria} y {@code /calendario} con la 006— y con la 007 se
     * cumplio la ultima, {@code /configuracion}. Dejarla habria sido un bucle sobre una
     * lista vacia: no afirma nada y pasa en silencio, que es peor que no tenerlo.
     *
     * <p>El riesgo cambio de sitio, asi que la comprobacion tambien. Ya no es «que
     * nadie ocupe una ruta ajena» sino «que la pantalla de configuracion no enlace a
     * ninguna que no exista»: es una pagina cuyo unico contenido son enlaces, y un
     * destino mal escrito ahi es un callejon sin salida que nada mas detectaria.
     */
    @Test
    @DisplayName("la configuracion no enlaza a ninguna ruta que no exista")
    void configuracionEnlazaRutasQueExisten() throws IOException {
        List<String> servidas = rutas();

        String plantilla = Files.readString(
                Path.of("src/main/resources/templates/settings/index.html"));

        Matcher m = Pattern.compile("th:href=\"@\\{(/[^}]+)\\}\"").matcher(plantilla);
        List<String> enlazadas = new ArrayList<>();
        while (m.find()) {
            String destino = m.group(1);
            if (!destino.startsWith("/css") && !destino.startsWith("/js")) {
                enlazadas.add(destino);
            }
        }

        assertThat(enlazadas)
                .as("la pantalla existe justamente para enlazar la administracion")
                .isNotEmpty();
        assertThat(servidas)
                .as("cada destino de /configuracion tiene que ser una ruta servida")
                .containsAll(enlazadas);
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
    @DisplayName("/calendario la sirve la agenda, no la administracion de feriados")
    void rutaDelCalendario() throws IOException {
        List<String> encontradas = rutas();

        assertThat(encontradas)
                .as("la vista de calendario es /calendario (insumo 31)")
                .contains("/calendario");

        // Esta es la asercion que hay que conservar de la version anterior del test:
        // /calendario estaba reservada precisamente para que la administracion de dias
        // no laborables no la ocupara. Sigue en /dias-no-laborables.
        String agenda = Files.readString(
                CONTROLADORES.resolve("agenda/AgendaController.java"));
        String feriados = Files.readString(
                CONTROLADORES.resolve("calendar/CalendarController.java"));

        assertThat(agenda).contains("@GetMapping(\"/calendario\")");
        assertThat(feriados)
                .as("los feriados viven en /dias-no-laborables, no en /calendario")
                .doesNotContain("@GetMapping(\"/calendario\")");
        assertThat(feriados).contains("/dias-no-laborables");
    }

    @Test
    @DisplayName("la actividad diaria vive en la ruta que fija el insumo")
    void rutaDeLaActividadDiaria() throws IOException {
        List<String> encontradas = rutas();

        // Estaba reservada desde la 003 y la 006 la ocupa. La comprobacion se invierte:
        // antes se exigia que NADIE la usara, ahora que exista.
        assertThat(encontradas)
                .as("«que hice hoy» es /actividad-diaria (insumo 33)")
                .contains("/actividad-diaria");
        assertThat(encontradas).noneMatch(r -> r.contains("daily-activity"));
    }

    @Test
    @DisplayName("la configuracion vive en la ruta que fija el insumo")
    void rutaDeLaConfiguracion() throws IOException {
        assertThat(rutas())
                .as("era la ultima ruta reservada que quedaba (insumo 36)")
                .contains("/configuracion");
    }

    @Test
    @DisplayName("el insumo reserva las rutas que este proyecto debe respetar")
    void inventarioDeRutasReservadas() {
        // Deja constancia de la lista para quien construya las siguientes fases.
        assertThat(FIJADAS_POR_EL_INSUMO).hasSize(9);
    }
}
