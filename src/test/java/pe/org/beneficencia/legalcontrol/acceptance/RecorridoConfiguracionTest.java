package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * La administracion se alcanza <b>sin escribir ninguna URL</b>.
 *
 * <p>Es la unica prueba que comprueba lo que esta feature vino a arreglar. Un contrato
 * de MockMvc puede afirmar que la pantalla contiene la cadena {@code /prioridades} y
 * seguir sin demostrar que alguien llegue: lo que faltaba no era el destino sino el
 * camino. Aqui se recorre pulsando, empezando desde el panel.
 *
 * <p>Todos los datos son inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoConfiguracionTest extends PostgresIntegrationTest {

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
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(erroresDeConsola::add);
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void entrarComo(String correo) {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        pagina.waitForFunction(
                "() => document.activeElement && document.activeElement.id === 'email'");
        pagina.fill("#email", correo);
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    @Test
    @DisplayName("la jefa llega a un catalogo desde el panel y agrega un valor, sin teclear URLs")
    void desdeElPanelHastaElCatalogo() {
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/"));

        // Solo pulsando: es exactamente lo que antes era imposible.
        pagina.locator("nav a:has-text('Configuración')").click();
        pagina.waitForURL(u -> u.contains("/configuracion"));

        pagina.locator("main a:has-text('Tipos de pendiente')").click();
        pagina.waitForURL(u -> u.contains("/tipos-de-pendiente"));

        pagina.locator("#name").waitFor();
        pagina.fill("#name", "Diligencia externa");
        pagina.locator("form button:has-text('Agregar')").click();
        pagina.waitForURL(u -> u.contains("/tipos-de-pendiente"));

        assertThat(pagina.content()).contains("Diligencia externa");

        Integer creado = jdbc.sql("""
                SELECT count(*) FROM pending_task_type WHERE name = 'Diligencia externa'
                """).query(Integer.class).single();
        assertThat(creado).isEqualTo(1);

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("los seis destinos comunes se alcanzan pulsando")
    void losSeisDestinosSeAlcanzan() {
        entrarComo("abogado@ejemplo.test");

        // Cinco de estos no tenian ningun enlace entrante antes de esta pantalla.
        for (String enlace : List.of("Días no laborables y feriados", "Tipos de pendiente",
                "Prioridades", "Estados de pendiente", "Estados procesales",
                "Estados de procedimientos administrativos")) {
            pagina.navigate(url("/configuracion"));
            pagina.locator("main a:has-text('" + enlace + "')").first().click();
            pagina.waitForLoadState();

            assertThat(pagina.url())
                    .as("«%s» tiene que llevar a alguna parte", enlace)
                    .doesNotContain("/configuracion");
            assertThat(pagina.locator("h1").count())
                    .as("«%s» lleva a una pantalla real, no a un error", enlace)
                    .isPositive();
        }

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("un abogado no ve el enlace a cuentas, y el servidor lo rechaza igual")
    void elAbogadoNoVeCuentas() {
        entrarComo("abogado@ejemplo.test");
        pagina.navigate(url("/configuracion"));

        assertThat(pagina.locator("main a:has-text('Cuentas de usuario')").count())
                .as("no se ofrece algo que devolveria un rechazo")
                .isZero();

        assertThat(erroresDeConsola).isEmpty();

        // Y si escribe la direccion, el servidor manda: ocultar no es autorizar.
        var respuesta = pagina.navigate(url("/usuarios"));

        assertThat(respuesta.status())
                .as("la autorizacion vive en el servidor, no en la plantilla")
                .isEqualTo(403);
        assertThat(pagina.content()).doesNotContain("Agregar");

        // La consola se comprueba ANTES de este paso, no despues: un 403 deliberado
        // aparece ahi como «Failed to load resource», y exigir consola limpia
        // convertiria el comportamiento correcto en un fallo.
    }

    @Test
    @DisplayName("la jefa si ve el enlace a cuentas y llega a la pantalla")
    void laJefaSiVeCuentas() {
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/configuracion"));

        pagina.locator("main a:has-text('Cuentas de usuario')").click();
        pagina.waitForURL(u -> u.contains("/usuarios"));

        assertThat(pagina.locator("h1").count()).isPositive();
        assertThat(erroresDeConsola).isEmpty();
    }
}
