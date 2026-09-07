package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.microsoft.playwright.options.AriaRole;

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Recorrido con navegador real: teclado, foco y degradacion sin JavaScript.
 *
 * <p>Esto es lo que MockMvc no puede comprobar. MockMvc ve el HTML que sale del
 * servidor; aqui se comprueba lo que le pasa a una persona que solo usa el
 * teclado, y lo que ocurre cuando la red falla a medias y HTMX no llega a
 * cargarse.
 *
 * <p><b>La aplicacion debe funcionar sin JavaScript.</b> HTMX es una mejora, no
 * un requisito: en una conexion mala el script puede no llegar, y los formularios
 * tienen que seguir siendo GET y POST normales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessibilityAcceptanceTest extends PostgresIntegrationTest {

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;

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
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        pagina = navegador.newPage();
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    /**
     * Entra usando solo el teclado, como lo haria quien no puede usar el raton.
     *
     * <p>Se tabula desde el principio en vez de confiar en {@code autofocus}: es
     * lo que hace realmente quien llega a la pagina con el teclado, y ademas
     * Chromium sin ventana no aplica autofocus.
     */
    private void entrarConTeclado() {
        pagina.navigate(url("/login"));
        // Esperar al formulario antes de tabular: si no, con la maquina cargada
        // la primera pulsacion llega antes de que la pagina sea interactiva y el
        // texto se pierde.
        pagina.locator("#email").waitFor();
        // autofocus ya dejo el cursor en el correo; tabular aqui lo pasaria de largo.
        pagina.keyboard().type("abogado@ejemplo.test");
        pagina.keyboard().press("Tab");
        pagina.keyboard().type(SesionDePrueba.CONTRASENA);
        pagina.keyboard().press("Enter");
        pagina.waitForURL("**/judiciales**");
    }

    @Test
    @DisplayName("se puede iniciar sesion sin tocar el raton")
    void ingresoSoloConTeclado() {
        entrarConTeclado();

        assertThat(pagina.url()).contains("/judiciales");
        assertThat(pagina.content()).contains("Procesos judiciales");
    }

    @Test
    @DisplayName("el foco empieza en el primer campo y avanza en orden logico")
    void ordenDeFoco() {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();

        // El primer campo recibe el foco solo: quien llega con teclado escribe ya.
        assertThat(pagina.evaluate("() => document.activeElement.id").toString())
                .isEqualTo("email");

        // Sin tabindex inventados, el orden de foco es el del documento.
        pagina.keyboard().press("Tab");
        assertThat(pagina.evaluate("() => document.activeElement.id").toString())
                .isEqualTo("password");

        pagina.keyboard().press("Tab");
        assertThat(pagina.evaluate("() => document.activeElement.tagName").toString())
                .isEqualTo("BUTTON");
    }

    @Test
    @DisplayName("el formulario de alta no salta campos al tabular")
    void ordenDeFocoEnElAlta() {
        entrarConTeclado();
        pagina.navigate(url("/judiciales/nuevo"));
        pagina.locator("#caseNumber").waitFor();

        // Se tabula por todo el formulario recogiendo los campos alcanzados.
        java.util.List<String> alcanzados = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            pagina.keyboard().press("Tab");
            String id = pagina.evaluate("() => document.activeElement.id || ''").toString();
            if (!id.isBlank() && !alcanzados.contains(id)) {
                alcanzados.add(id);
            }
        }

        assertThat(alcanzados)
                .as("los campos obligatorios y las fechas deben ser alcanzables con teclado")
                .contains("caseNumber", "claimant", "respondent", "subject", "deadline");
    }

    @Test
    @DisplayName("el elemento enfocado se distingue a simple vista")
    void focoVisible() {
        pagina.navigate(url("/login"));
        pagina.focus("#password");

        // Sin contorno visible, quien navega con teclado se pierde en la pagina.
        String contorno = pagina.evaluate("""
                () => {
                  const e = document.activeElement;
                  const s = getComputedStyle(e);
                  return s.outlineStyle + ' ' + s.outlineWidth;
                }
                """).toString();

        assertThat(contorno).doesNotStartWith("none");
    }

    @Test
    @DisplayName("la aplicacion sigue siendo utilizable si HTMX no llega a cargarse")
    void funcionaSinJavaScript() {
        // Se corta la descarga del script, como en una conexion que falla a medias.
        pagina.route("**/vendor/htmx.min.js", ruta -> ruta.abort());

        pagina.navigate(url("/login"));
        pagina.fill("#email", "abogado@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL("**/judiciales**");

        assertThat(pagina.content()).contains("Procesos judiciales");

        // Registrar un expediente tampoco puede depender del script.
        pagina.navigate(url("/judiciales/nuevo"));
        pagina.fill("#caseNumber", "EXP-SINJS-2026");
        pagina.click("button[type=submit]");

        Integer guardados = jdbc.sql(
                        "SELECT count(*) FROM judicial_case WHERE case_number = 'EXP-SINJS-2026'")
                .query(Integer.class).single();
        assertThat(guardados).as("el alta debe funcionar sin JavaScript").isEqualTo(1);
    }

    @Test
    @DisplayName("los errores se anuncian a un lector de pantalla, no solo en rojo")
    void erroresAnunciados() {
        pagina.navigate(url("/login"));
        pagina.fill("#email", "abogado@ejemplo.test");
        pagina.fill("#password", "contrasena equivocada");
        pagina.click("button[type=submit]");
        pagina.waitForURL("**/login?error**");

        var aviso = pagina.getByRole(AriaRole.ALERT);

        assertThat(aviso.count()).as("el error debe tener role=alert").isPositive();
        assertThat(aviso.first().textContent().strip())
                .as("y decir algo, no solo pintarse de rojo").isNotEmpty();
    }

    @Test
    @DisplayName("cada campo del formulario se anuncia con su etiqueta")
    void camposConNombreAccesible() {
        entrarConTeclado();
        pagina.navigate(url("/judiciales/nuevo"));

        // getByLabel solo encuentra el campo si su label esta bien asociada.
        assertThat(pagina.getByLabel("N.º de expediente").count()).isPositive();
        assertThat(pagina.getByLabel("Demandante").count()).isPositive();
        assertThat(pagina.getByLabel("Fecha limite").count()).isPositive();
    }

    @Test
    @DisplayName("el estado del plazo se lee como texto, no solo por color")
    void plazoLegibleSinColor() {
        entrarConTeclado();

        pagina.navigate(url("/judiciales/nuevo"));
        pagina.fill("#caseNumber", "EXP-PLAZO-2026");
        pagina.fill("#deadline", java.time.LocalDate.now().plusDays(20).toString());
        pagina.click("button[type=submit]");

        pagina.navigate(url("/judiciales"));
        String texto = pagina.locator("table").textContent();

        // Sin calendario revisado el sistema avisa en vez de inventar un numero.
        assertThat(texto).containsAnyOf("Calculo no disponible", "dias habiles restantes",
                "Vence hoy", "Vencido");
    }

    @Test
    @DisplayName("los textos salen traducidos, no como claves sin resolver")
    void textosResueltos() {
        // Con un navegador que pide es-PE y solo messages_es.properties, Thymeleaf
        // pintaba «??acceso.titulo_es_PE??» en vez del texto. Esto lo caza.
        pagina.navigate(url("/login"));
        String html = pagina.content();

        assertThat(html).as("ninguna clave de traduccion debe llegar sin resolver")
                .doesNotContain("??");
        assertThat(pagina.title()).isEqualTo("Iniciar sesion");
    }

    @Test
    @DisplayName("la pagina declara el idioma para que el lector la pronuncie bien")
    void idiomaDeclarado() {
        pagina.navigate(url("/login"));
        assertThat(pagina.getAttribute("html", "lang")).isEqualTo("es");
    }

    @Test
    @DisplayName("el listado vacio ofrece una salida alcanzable con teclado")
    void estadoVacioConSalida() {
        entrarConTeclado();
        pagina.navigate(url("/judiciales?q=noexisteestenumero"));

        var quitar = pagina.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Quitar los filtros"));

        assertThat(quitar.count()).as("un listado vacio no puede ser un callejon").isPositive();
    }
}
