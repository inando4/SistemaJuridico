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
        // Esperar a que el campo EXISTA no basta: el navegador aplica el autofocus
        // despues, y escribir en ese hueco manda el texto fuera del campo. El
        // sintoma era desconcertante —el correo terminaba en blanco y la
        // contrasena, escrita tras el Tab, aparecia dentro de #email— y con la
        // maquina cargada ocurria una vez de cada tantas.
        //
        // Se espera al foco real, no a un retardo fijo: es la condicion que de
        // verdad hace falta, y no depende de lo ocupada que este la maquina.
        pagina.waitForFunction(
                "() => document.activeElement && document.activeElement.id === 'email'");

        // El texto se inserta de una vez en el campo enfocado, no tecla a tecla.
        // Lo que estas pruebas comprueban es el ORDEN DEL FOCO —que se llegue a
        // cada campo sin tocar el raton—, no como el navegador procesa cada tecla:
        // insertText conserva lo primero y quita la fragilidad de lo segundo,
        // porque sigue escribiendo solo donde el foco haya llegado por si solo.
        pagina.keyboard().insertText("abogado@ejemplo.test");
        pagina.keyboard().press("Tab");
        pagina.keyboard().insertText(SesionDePrueba.CONTRASENA);

        // Comprobar lo tecleado antes de enviar: si algo se perdio, el fallo debe
        // señalar el tecleo y no un timeout generico diez lineas mas abajo.
        assertThat(pagina.locator("#email").inputValue())
                .as("el correo tiene que llegar entero al campo")
                .isEqualTo("abogado@ejemplo.test");

        pagina.keyboard().press("Enter");
        try {
            pagina.waitForURL(u -> !u.contains("/login"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30_000));
        } catch (RuntimeException e) {
            throw new AssertionError("No se salio de /login. URL actual: " + pagina.url(), e);
        }
    }

    @Test
    @DisplayName("se puede iniciar sesion sin tocar el raton")
    void ingresoSoloConTeclado() {
        entrarConTeclado();

        // Se aterriza en el panel del dia, no en expedientes (insumo, seccion 23).
        assertThat(pagina.url()).doesNotContain("/login");
        assertThat(pagina.content()).contains("¿Qué tengo que hacer hoy?");
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
        pagina.waitForURL(u -> !u.contains("/login"));

        // El panel tambien tiene que servir sin JavaScript.
        assertThat(pagina.content()).contains("¿Qué tengo que hacer hoy?");

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
        assertThat(pagina.getByLabel("Fecha límite").count()).isPositive();
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
        assertThat(texto).containsAnyOf("Cálculo no disponible", "días hábiles restantes",
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
        assertThat(pagina.title()).isEqualTo("Iniciar sesión");
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

    @Test
    @DisplayName("un procedimiento administrativo se registra sin JavaScript")
    void altaAdministrativaSinJavaScript() {
        pagina.route("**/vendor/htmx.min.js", ruta -> ruta.abort());

        pagina.navigate(url("/login"));
        pagina.fill("#email", "abogado@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL(u -> !u.contains("/login"));

        pagina.navigate(url("/administrativos/nuevo"));
        pagina.fill("#fileNumber", "ADM-SINJS-2026");
        pagina.fill("#requestingArea", "Gerencia General");
        pagina.click("button[type=submit]");

        Integer guardados = jdbc.sql("""
                SELECT count(*) FROM administrative_procedure
                WHERE file_number = 'ADM-SINJS-2026'
                """).query(Integer.class).single();
        assertThat(guardados).as("el alta debe funcionar sin JavaScript").isEqualTo(1);
    }

    @Test
    @DisplayName("el panel del dia se recorre con teclado y sus tarjetas son enlaces")
    void panelConTeclado() {
        entrarConTeclado();

        // Las tarjetas son enlaces, no divs con onclick: con teclado se alcanzan
        // tabulando y se abren con Enter, sin necesidad de raton ni de script.
        java.util.List<String> destinos = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pagina.keyboard().press("Tab");
            String href = pagina.evaluate(
                    "() => document.activeElement.getAttribute('href') || ''").toString();
            if (!href.isBlank() && !destinos.contains(href)) {
                destinos.add(href);
            }
        }

        assertThat(destinos)
                .as("cada tarjeta debe llevar al listado de lo que cuenta")
                .anyMatch(d -> d.contains("alerta=vencidos"))
                .anyMatch(d -> d.contains("alerta=hoy"))
                .anyMatch(d -> d.contains("alerta=activos"));
    }

    @Test
    @DisplayName("el panel y las alertas funcionan sin JavaScript")
    void panelYAlertasSinJavaScript() {
        pagina.route("**/vendor/htmx.min.js", ruta -> ruta.abort());

        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        pagina.fill("#email", "abogado@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL(u -> !u.contains("/login"));

        assertThat(pagina.content()).contains("¿Qué tengo que hacer hoy?");

        // Y se navega a las alertas por un enlace normal, no por un fetch.
        pagina.click("nav a[href='/alertas']");
        pagina.waitForURL("**/alertas**");
        assertThat(pagina.content()).contains("Alertas");
    }

    @Test
    @DisplayName("desde el panel se llega a las alertas y de vuelta")
    void idaYVueltaAlPanel() {
        entrarConTeclado();

        pagina.click("nav a[href='/alertas']");
        pagina.waitForURL("**/alertas**");

        pagina.click("nav a[href='/']");
        pagina.waitForURL(u -> u.endsWith("/"));
        assertThat(pagina.content()).contains("¿Qué tengo que hacer hoy?");
    }

    @Test
    @DisplayName("el formulario administrativo se recorre entero con teclado")
    void ordenDeFocoEnElAltaAdministrativa() {
        entrarConTeclado();
        pagina.navigate(url("/administrativos/nuevo"));
        pagina.locator("#fileNumber").waitFor();

        java.util.List<String> alcanzados = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            pagina.keyboard().press("Tab");
            String id = pagina.evaluate("() => document.activeElement.id || ''").toString();
            if (!id.isBlank() && !alcanzados.contains(id)) {
                alcanzados.add(id);
            }
        }

        assertThat(alcanzados)
                .as("todos los campos deben ser alcanzables con teclado")
                .contains("fileNumber", "requestingArea", "request", "receivedAt", "deadline");
    }

    @Test
    @DisplayName("un pendiente se cumple y se revierte sin JavaScript")
    void cumplirYRevertirSinJavaScript() {
        pagina.route("**/vendor/htmx.min.js", ruta -> ruta.abort());

        pagina.navigate(url("/login"));
        pagina.fill("#email", "abogado@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL(u -> !u.contains("/login"));

        pagina.navigate(url("/pendientes/nuevo"));
        pagina.fill("#title", "Tarea sin JavaScript");
        pagina.click("button[type=submit]");

        // Cumplir.
        pagina.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Marcar como cumplido")).click();

        Integer cumplidos = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE title = 'Tarea sin JavaScript' AND completed_at IS NOT NULL
                """).query(Integer.class).single();
        assertThat(cumplidos).as("cumplir debe funcionar sin JavaScript").isEqualTo(1);

        // Revertir, que exige motivo.
        pagina.fill("#motivoReversion", "Prueba de accesibilidad");
        pagina.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Revertir cumplimiento")).click();

        Integer activos = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE title = 'Tarea sin JavaScript' AND completed_at IS NULL
                """).query(Integer.class).single();
        assertThat(activos).as("revertir tambien").isEqualTo(1);
    }

    @Test
    @DisplayName("el campo de motivo de reversion se anuncia como obligatorio")
    void motivoDeReversionAnunciado() {
        entrarConTeclado();

        pagina.navigate(url("/pendientes/nuevo"));
        pagina.locator("#title").waitFor();
        pagina.fill("#title", "Tarea para revertir");
        pagina.click("button[type=submit]");

        pagina.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Marcar como cumplido")).click();

        // Sin label asociada, un lector de pantalla no diria que se pide un motivo.
        assertThat(pagina.getByLabel("Motivo de la reversión").count()).isPositive();
        assertThat(pagina.getAttribute("#motivoReversion", "required")).isNotNull();
    }
}
