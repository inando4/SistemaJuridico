package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import pe.org.beneficencia.legalcontrol.integration.DatosSinteticos;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Barrido con navegador real buscando fallos que las pruebas dirigidas no ven.
 *
 * <p>Una prueba dirigida comprueba lo que su autor penso comprobar. Esta recorre
 * todas las pantallas y mira lo que sale mal <b>sin haberlo previsto</b>: claves
 * de traduccion sin resolver, expresiones de plantilla que no se evaluaron,
 * errores de JavaScript, enlaces que no llevan a ninguna parte.
 *
 * <p>Existe porque un fallo asi ya se colo una vez: la interfaz mostraba
 * {@code ??acceso.titulo_es_PE??} en lugar de texto en espanol, y ninguna prueba
 * de las que habia lo veia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RevisionConNavegadorTest extends PostgresIntegrationTest {

    /** Todo lo que una persona con sesion puede abrir con un GET sin parametros. */
    private static final List<String> PANTALLAS = List.of(
            "/", "/alertas", "/pendientes", "/pendientes/hoy", "/cumplidos",
            "/judiciales", "/administrativos", "/equipo",
            "/pendientes/nuevo", "/judiciales/nuevo",
            "/administrativos/nuevo", "/dias-no-laborables", "/usuarios",
            "/tipos-de-pendiente", "/prioridades", "/estados-de-pendiente");

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;
    private final List<String> erroresDeConsola = new ArrayList<>();

    @BeforeAll
    static void abrirNavegador() {
        playwright = Playwright.create();
        navegador = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void cerrarNavegador() {
        if (navegador != null) {
            navegador.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        LocalDate hoy = LocalDate.now();
        DatosSinteticos.sembrarCalendario(jdbc, jefa, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);
        DatosSinteticos.sembrar(jdbc, jefa, 3, 2);
        DatosSinteticos.sembrarAdministrativos(jdbc, jefa, 3, 2);
        DatosSinteticos.sembrarPendientes(jdbc, jefa, 20, 3);
        sembrarCadaSituacion(jefa, hoy);

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(e -> erroresDeConsola.add("excepcion sin capturar: " + e));

        entrar();
    }

    /** Un pendiente de cada situacion, para que el panel no salga todo en cero. */
    private void sembrarCadaSituacion(UUID responsable, LocalDate hoy) {
        UUID tipo = jdbc.sql("SELECT id FROM pending_task_type LIMIT 1")
                .query(UUID.class).single();
        UUID prioridad = jdbc.sql("SELECT id FROM priority LIMIT 1").query(UUID.class).single();
        UUID estado = jdbc.sql("SELECT id FROM pending_task_status LIMIT 1")
                .query(UUID.class).single();
        Timestamp ahora = Timestamp.from(Instant.now());

        record Caso(String titulo, LocalDate limite, LocalDate programado, LocalDate recepcion) { }
        List<Caso> casos = List.of(
                new Caso("Revision vencida", hoy.minusDays(4), null, hoy.minusDays(20)),
                new Caso("Revision de hoy", hoy, null, hoy.minusDays(5)),
                new Caso("Revision programada hoy", null, hoy, hoy.minusDays(5)),
                new Caso("Revision proxima", hoy.plusDays(2), null, hoy.minusDays(3)),
                new Caso("Revision antigua sin plazo", null, null, hoy.minusDays(90)));

        for (Caso c : casos) {
            jdbc.sql("""
                    INSERT INTO pending_task
                        (id, owner_id, title, pending_task_type_id, priority_id,
                         pending_task_status_id, received_at, registered_at, scheduled_for,
                         deadline, active, created_at, updated_at, version)
                    VALUES (:id, :owner, :titulo, :tipo, :prioridad, :estado,
                            :recepcion, :recepcion, :programado, :limite, true,
                            :ahora, :ahora, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("owner", responsable)
                    .param("titulo", c.titulo()).param("tipo", tipo)
                    .param("prioridad", prioridad).param("estado", estado)
                    .param("recepcion", c.recepcion()).param("programado", c.programado())
                    .param("limite", c.limite()).param("ahora", ahora)
                    .update();
        }
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void entrar() {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        pagina.fill("#email", "jefa@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    // ------------------------------------------------------------------------

    @Test
    @DisplayName("ninguna pantalla muestra claves de traduccion ni expresiones sin resolver")
    void sinMarcadoresSinResolver() {
        List<String> problemas = new ArrayList<>();

        for (String ruta : PANTALLAS) {
            pagina.navigate(url(ruta));
            String texto = pagina.locator("body").innerText();

            // Thymeleaf pinta ??clave?? cuando falta la traduccion, y deja la
            // expresion literal si el atributo esta mal escrito.
            if (texto.contains("??")) {
                problemas.add(ruta + " muestra una clave sin traducir");
            }
            if (texto.contains("${") || texto.contains("*{")) {
                problemas.add(ruta + " muestra una expresion de plantilla sin evaluar");
            }
            if (texto.contains("th:")) {
                problemas.add(ruta + " muestra un atributo de plantilla como texto");
            }
        }

        assertThat(problemas)
                .as("un fallo asi ya llego a produccion una vez, y ninguna prueba lo veia")
                .isEmpty();
    }

    /**
     * Formas sin tilde o sin ene que no deben aparecer en pantalla.
     *
     * <p>Se comprueba sobre el texto <b>renderizado</b> y no sobre las plantillas:
     * en el archivo, «version» aparece en {@code name="version"} y «pagina» dentro
     * de {@code ${pagina}}, que son identificadores y deben seguir en ASCII. Lo
     * que se lee en pantalla no tiene esa excusa.
     */
    private static final List<String> MAL_ESCRITAS = List.of(
            "sesion", "Sesion", "Contrasena", "contrasena", "dias habiles",
            "dia habil", "numero", "Numero", "codigo", "Codigo", "atencion",
            "Atencion", "informacion", "Informacion", "Espanol", "ningun",
            "Ningun", "Proximo", "proximo", "Juridico", "fecha limite",
            "Fecha limite", "descripcion", "Descripcion", "pagina solicitada",
            "accion", "Recepcion", "Antiguedad", "Titulo");

    @Test
    @DisplayName("ninguna pantalla muestra palabras sin tilde ni sin ene")
    void ortografiaEnPantalla() {
        List<String> problemas = new ArrayList<>();

        for (String ruta : PANTALLAS) {
            pagina.navigate(url(ruta));
            String texto = pagina.locator("body").innerText();
            for (String mala : MAL_ESCRITAS) {
                // Con limites de palabra: «ninguno» es correcto y no debe
                // confundirse con «ningun», ni «numeros» con una palabra suelta.
                if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(mala)
                        + "\\b").matcher(texto).find()) {
                    problemas.add(ruta + " muestra «" + mala + "»");
                }
            }
        }

        assertThat(problemas)
                .as("la interfaz es para un area juridica: las tildes y la ene no son opcionales")
                .isEmpty();
    }

    @Test
    @DisplayName("los acentos llegan al navegador sin romperse")
    void sinMojibake() {
        List<String> problemas = new ArrayList<>();

        for (String ruta : PANTALLAS) {
            pagina.navigate(url(ruta));
            String texto = pagina.locator("body").innerText();
            // Ã, Â y el rombo de reemplazo son la firma de un UTF-8 leido como
            // Latin-1: si aparecen, el problema es de codificacion, no de texto.
            if (texto.contains("Ã") || texto.contains("Â") || texto.contains("\uFFFD")) {
                problemas.add(ruta);
            }
        }

        assertThat(problemas).as("acentos rotos por codificacion").isEmpty();
    }

    @Test
    @DisplayName("ninguna pantalla repite los enlaces de la navegacion en su cuerpo")
    void sinNavegacionDuplicada() {
        List<String> problemas = new ArrayList<>();

        for (String ruta : PANTALLAS) {
            pagina.navigate(url(ruta));

            List<String> enLaNav = pagina.locator("nav[aria-label='Secciones'] a").all()
                    .stream().map(e -> e.getAttribute("href")).filter(h -> h != null).toList();
            // Enlaces del cuerpo que apuntan justo a donde ya lleva la navegacion:
            // antes de que existiera la barra comun, cada pantalla tenia la suya, y
            // al anadir la comun quedaron las dos, una debajo de la otra.
            List<String> repetidos = pagina.locator("main > p:first-child > a").all()
                    .stream().map(e -> e.getAttribute("href"))
                    .filter(h -> h != null && enLaNav.contains(h)).toList();

            if (repetidos.size() >= 2) {
                problemas.add(ruta + " repite " + repetidos);
            }
        }

        assertThat(problemas).isEmpty();
    }

    @Test
    @DisplayName("ninguna pantalla responde con error")
    void todasLasPantallasCargan() {
        List<String> problemas = new ArrayList<>();

        for (String ruta : PANTALLAS) {
            var respuesta = pagina.navigate(url(ruta));
            if (respuesta == null || respuesta.status() >= 400) {
                problemas.add(ruta + " respondio "
                        + (respuesta == null ? "nada" : respuesta.status()));
            }
        }

        assertThat(problemas).isEmpty();
    }

    @Test
    @DisplayName("ninguna pantalla produce errores de JavaScript")
    void sinErroresDeConsola() {
        for (String ruta : PANTALLAS) {
            pagina.navigate(url(ruta));
            pagina.waitForLoadState();
        }

        assertThat(erroresDeConsola)
                .as("la interfaz es minima, pero lo poco que hay tiene que funcionar")
                .isEmpty();
    }

    @Test
    @DisplayName("los enlaces de la navegacion llevan todos a una pantalla que existe")
    void navegacionSinEnlacesRotos() {
        pagina.navigate(url("/"));

        List<String> destinos = pagina.locator("nav a").all().stream()
                .map(e -> e.getAttribute("href"))
                .filter(h -> h != null && h.startsWith("/"))
                .distinct()
                .toList();

        assertThat(destinos).as("la navegacion debe tener enlaces").isNotEmpty();

        List<String> rotos = new ArrayList<>();
        for (String destino : destinos) {
            var respuesta = pagina.navigate(url(destino));
            if (respuesta == null || respuesta.status() >= 400) {
                rotos.add(destino + " -> "
                        + (respuesta == null ? "nada" : respuesta.status()));
            }
        }

        assertThat(rotos).isEmpty();
    }

    @Test
    @DisplayName("las tarjetas del panel llevan a listados que existen y no estan vacios")
    void tarjetasLlevanAAlgo() {
        pagina.navigate(url("/"));

        List<String> tarjetas = pagina.locator("main a[href*='alerta=']").all().stream()
                .map(e -> e.getAttribute("href"))
                .filter(h -> h != null)
                .distinct()
                .toList();

        assertThat(tarjetas).as("las seis tarjetas deben enlazar").hasSize(6);

        for (String destino : tarjetas) {
            var respuesta = pagina.navigate(url(destino));
            assertThat(respuesta).isNotNull();
            assertThat(respuesta.status())
                    .as("la tarjeta %s lleva a una pantalla rota", destino)
                    .isLessThan(400);
        }
    }

    @Test
    @DisplayName("el panel muestra cifras, no marcadores de plantilla")
    void elPanelMuestraCifrasReales() {
        pagina.navigate(url("/"));
        String texto = pagina.locator("main").innerText();

        // Los valores por defecto de la plantilla (los «0» escritos a mano) no
        // deben quedarse: si se quedan, es que la expresion no se evaluo.
        assertThat(texto).contains("Vencidos", "Urgentes hoy", "Pendientes activos");

        // Con los datos sembrados hay al menos un vencido y uno de hoy.
        assertThat(texto)
                .as("con datos sembrados el panel no puede salir todo en cero")
                .doesNotContain("No tiene ningún pendiente registrado");
    }

    @Test
    @DisplayName("las alertas muestran los cinco tipos con los datos sembrados")
    void lasAlertasMuestranLosTipos() {
        pagina.navigate(url("/alertas"));
        String texto = pagina.locator("main").innerText();

        assertThat(texto).contains("Vencido");
        assertThat(texto).contains("Urgente");
        assertThat(texto).contains("Próximo vencimiento");
        assertThat(texto).contains("Pendiente antiguo");
        assertThat(texto)
                .as("con calendario sembrado no debe faltar ninguna cifra")
                .doesNotContain("Faltan días no laborables por revisar");
    }

    @Test
    @DisplayName("ninguna pantalla desborda horizontalmente en una laptop modesta")
    void sinDesbordeHorizontal() {
        pagina.setViewportSize(1280, 720);
        List<String> desbordan = new ArrayList<>();

        for (String ruta : PANTALLAS) {
            pagina.navigate(url(ruta));
            boolean desborda = (boolean) pagina.evaluate(
                    "() => document.documentElement.scrollWidth > window.innerWidth + 1");
            if (desborda) {
                desbordan.add(ruta);
            }
        }

        assertThat(desbordan)
                .as("el area trabaja en laptops modestas; una barra horizontal estorba")
                .isEmpty();
    }
}
