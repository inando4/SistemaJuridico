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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** Verifica la preferencia visual y su independencia de datos, red y formularios. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModosPresentacionTest extends PostgresIntegrationTest {

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

        pagina = navegador.newContext().newPage();
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

    @org.junit.jupiter.api.AfterEach
    void cerrarPagina() { pagina.context().close(); }

    @Test
    void conservaFormularioPreferenciaYTeclado() {
        pagina.navigate(url("/pendientes/nuevo"));
        pagina.fill("#title", "Informe sin guardar");
        var selector = pagina.locator("#modo-presentacion");
        assertThat(selector.getAttribute("aria-checked")).isEqualTo("true");
        selector.focus();
        pagina.keyboard().press("Space");
        assertThat(selector.getAttribute("aria-checked")).isEqualTo("false");
        assertThat(pagina.locator("#title").inputValue()).isEqualTo("Informe sin guardar");
        assertThat(pagina.evaluate("document.activeElement.id")).isEqualTo("modo-presentacion");
        assertThat(pagina.evaluate("document.getElementById('estandar-css').disabled")).isEqualTo(true);
        pagina.keyboard().press("Enter");
        assertThat(selector.getAttribute("aria-checked")).isEqualTo("true");
        assertThat(pagina.locator("#title").inputValue()).isEqualTo("Informe sin guardar");
        selector.click();
        var solicitudes = new ArrayList<String>();
        pagina.onRequest(r -> solicitudes.add(r.url()));
        pagina.reload();
        assertThat(selector.getAttribute("aria-checked")).isEqualTo("false");
        assertThat(solicitudes).noneMatch(u -> u.matches(".*/css/estandar(?:-[a-f0-9]{32})?\\.css"));
        assertThat(pagina.locator("#estandar-css").count()).isZero();
        pagina.navigate(url("/judiciales"));
        assertThat(selector.getAttribute("aria-checked")).isEqualTo("false");
        selector.click();
        pagina.reload();
        assertThat(selector.getAttribute("aria-checked")).isEqualTo("true");
        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    void sincronizaEntrePestanas() {
        var otra = pagina.context().newPage();
        otra.navigate(url("/"));
        pagina.locator("#modo-presentacion").click();
        otra.waitForFunction("document.documentElement.dataset.presentacion === 'rendimiento'");
        assertThat(otra.locator("#modo-presentacion").getAttribute("aria-checked")).isEqualTo("false");
        otra.locator("#modo-presentacion").click();
        pagina.waitForFunction("document.documentElement.dataset.presentacion === 'estandar'");
        otra.close();
    }

    @Test
    void anticipaElEstiloSinMostrarElModoEquivocado() {
        try (var contexto = navegador.newContext()) {
            contexto.addInitScript("""
                    window.primeraVista = null;
                    requestAnimationFrame(function observar() {
                      const cuerpo = document.body;
                      if (cuerpo && cuerpo.querySelector('main') && getComputedStyle(cuerpo).visibility !== 'hidden') {
                        window.primeraVista = {
                          modo: document.documentElement.dataset.presentacion,
                          conEstilo: [...document.styleSheets].some(s => /\\/estandar-[a-f0-9]+\\.css$/.test(s.href || ''))
                        };
                      } else requestAnimationFrame(observar);
                    });
                    """);
            // Retrasar el script no debe retrasar el descubrimiento de la hoja.
            contexto.route("**/js/presentacion-*.js", ruta -> {
                try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                ruta.resume();
            });
            var lenta = contexto.newPage();
            lenta.navigate(url("/login"));
            lenta.waitForFunction("window.primeraVista !== null");
            assertThat(lenta.evaluate("window.primeraVista.modo")).isEqualTo("estandar");
            assertThat(lenta.evaluate("window.primeraVista.conEstilo")).isEqualTo(true);
            assertThat(lenta.evaluate("""
                    () => {
                      const recursos = performance.getEntriesByType('resource');
                      const css = recursos.filter(r => /\\/estandar-[a-f0-9]+\\.css$/.test(r.name));
                      const js = recursos.find(r => /\\/presentacion-[a-f0-9]+\\.js$/.test(r.name));
                      return css.length === 1 && css[0].startTime < js.responseEnd;
                    }
                    """)).isEqualTo(true);
        }
    }

    @Test
    void reutilizaLosRecursosEntrePantallasYNoDescargaHtmx() {
        pagina.navigate(url("/pendientes"));
        pagina.navigate(url("/judiciales"));
        assertThat(pagina.evaluate("""
                () => {
                  const recursos = performance.getEntriesByType('resource')
                    .filter(r => /\\/(css|js|vendor)\\//.test(r.name));
                  return recursos.length === 5 && recursos.every(r =>
                    /-[a-f0-9]{32}\\.(css|js)$/.test(r.name) && r.transferSize === 0 && !r.name.includes('htmx'));
                }
                """)).isEqualTo(true);
    }

    @Test
    void unErrorDelEstiloNoOcultaElFormulario() {
        try (var contexto = navegador.newContext()) {
            contexto.route("**/css/estandar-*.css", ruta -> ruta.abort());
            var sinEstilo = contexto.newPage();
            sinEstilo.navigate(url("/login"));
            assertThat(sinEstilo.locator("#email").isVisible()).isTrue();
            assertThat(sinEstilo.locator("html").getAttribute("data-presentacion-cargando")).isNull();
            sinEstilo.locator("#modo-presentacion").click();
            assertThat(sinEstilo.locator("#modo-presentacion").getAttribute("aria-checked")).isEqualTo("false");
        }
    }

    @Test
    void funcionaSinAlmacenamientoYSinJavaScript() {
        try (var contexto = navegador.newContext()) {
            contexto.addInitScript("Object.defineProperty(window, 'localStorage', {get() {throw new DOMException('Blocked', 'SecurityError')}})");
            var sinAlmacenamiento = contexto.newPage();
            sinAlmacenamiento.navigate(url("/login"));
            sinAlmacenamiento.locator("#modo-presentacion").click();
            assertThat(sinAlmacenamiento.locator("#modo-presentacion").getAttribute("aria-checked")).isEqualTo("false");
            assertThat(sinAlmacenamiento.locator("#email").isVisible()).isTrue();
        }
        try (var contexto = navegador.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var sinJs = contexto.newPage();
            sinJs.navigate(url("/login"));
            assertThat(sinJs.locator("#modo-presentacion").isVisible()).isFalse();
            sinJs.fill("#email", "jefa@ejemplo.test");
            sinJs.fill("#password", SesionDePrueba.CONTRASENA);
            sinJs.locator("button[type=submit]").click();
            sinJs.waitForURL(u -> !u.contains("/login"));
            assertThat(sinJs.locator("main h1").innerText()).contains("hacer hoy");
            sinJs.locator("nav summary").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Seguimiento")).click();
            sinJs.locator("nav a[href='/alertas']").click();
            assertThat(sinJs.url()).endsWith("/alertas");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 390, 1440})
    void tablasPobladasLegiblesEnAmbosModos(int ancho) {
        jdbc.sql("UPDATE pending_task SET completed_at = CURRENT_TIMESTAMP "
                + "WHERE id = (SELECT id FROM pending_task LIMIT 1)").update();
        UUID expediente = jdbc.sql("SELECT id FROM judicial_case LIMIT 1").query(UUID.class).single();
        UUID pendiente = jdbc.sql("SELECT id FROM pending_task LIMIT 1").query(UUID.class).single();
        var rutas = new ArrayList<>(PANTALLAS);
        rutas.addAll(List.of("/estados-procesales", "/estados-administrativos", "/calendario",
                "/judiciales/" + expediente, "/pendientes/" + pendiente,
                "/pendientes/" + pendiente + "/historial"));
        pagina.setViewportSize(ancho, 1000);
        for (String modo : List.of("estandar", "rendimiento")) {
            pagina.evaluate("m => localStorage.setItem('sistema-juridico.presentacion', m)", modo);
            for (String ruta : rutas) {
                assertThat(pagina.navigate(url(ruta)).status()).as(ruta).isEqualTo(200);
                pagina.waitForFunction("m => document.documentElement.dataset.presentacion === m", modo);
                assertThat((boolean) pagina.evaluate("document.documentElement.scrollWidth <= innerWidth + 1"))
                        .as("sin desborde en %s a %s px (%s)", ruta, ancho, modo).isTrue();
                assertThat((boolean) pagina.evaluate("""
                        () => [...document.querySelectorAll('main table')].every(t => {
                          const region = t.closest('[role=region]');
                          return region && region.tabIndex === 0 && region.getAttribute('aria-label') &&
                            [...t.querySelectorAll('th')].every(c => {
                              const r = c.getBoundingClientRect();
                              return r.width >= 80 && r.height < 150;
                            });
                        })
                        """)).as("tablas legibles y accesibles en %s a %s px (%s)", ruta, ancho, modo).isTrue();
            }
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "ui.capture", matches = "true")
    void revisionVisualEnAmbosModos() throws Exception {
        var evidencia = java.nio.file.Path.of(".impeccable/review");
        java.nio.file.Files.createDirectories(evidencia);
        for (int ancho : new int[]{1440, 390}) {
            pagina.setViewportSize(ancho, 1000);
            for (String modo : List.of("estandar", "rendimiento")) {
                pagina.evaluate("m => localStorage.setItem('sistema-juridico.presentacion', m)", modo);
                for (String ruta : List.of("/", "/pendientes", "/pendientes/nuevo", "/configuracion", "/calendario", "/login")) {
                    pagina.navigate(url(ruta));
                    pagina.waitForFunction("m => document.documentElement.dataset.presentacion === m", modo);
                    assertThat((boolean) pagina.evaluate("document.documentElement.scrollWidth <= innerWidth + 1"))
                        .as("sin desborde en %s a %s px (%s)", ruta, ancho, modo).isTrue();
                    String vista = ancho == 1440 ? "desktop" : "mobile";
                    String nombre = ruta.equals("/") ? vista : vista + "-" + ruta.substring(1).replace('/', '-');
                    if (modo.equals("rendimiento")) nombre += "-rendimiento";
                    pagina.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(evidencia.resolve(nombre + ".png")));
                    if (modo.equals("estandar") && ruta.equals("/")) {
                        pagina.locator("nav[aria-label='Secciones'] a[href='/pendientes']").focus();
                        double contraste = ((Number) pagina.evaluate("""
                            () => {
                                const luminancia = color => {
                                    const c = color.match(/[\\d.]+/g).slice(0, 3).map(v => {
                                        const s = Number(v) / 255;
                                        return s <= .04045 ? s / 12.92 : ((s + .055) / 1.055) ** 2.4;
                                    });
                                    return .2126*c[0] + .7152*c[1] + .0722*c[2];
                                };
                                const foco = luminancia(getComputedStyle(document.activeElement).outlineColor);
                                const fondo = luminancia(getComputedStyle(document.querySelector('nav[aria-label="Secciones"]')).backgroundColor);
                                return (Math.max(foco, fondo) + .05) / (Math.min(foco, fondo) + .05);
                            }
                            """)).doubleValue();
                        assertThat(contraste).as("contraste del foco sobre navegación oscura").isGreaterThanOrEqualTo(3);
                        java.nio.file.Files.writeString(evidencia.resolve(vista + "-foco.txt"), "Contraste de foco calculado en navegador: " + contraste + ":1\n");
                        pagina.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(evidencia.resolve(vista + "-navegacion-foco.png")));
                    }
                }
            }
        }
        assertThat(erroresDeConsola).isEmpty();
    }
}
