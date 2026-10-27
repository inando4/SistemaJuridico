package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Los recordatorios en un navegador de verdad: el color elegido y el dialogo de
 * confirmacion, que es JavaScript y ninguna prueba de MockMvc puede ejecutar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecordatoriosNavegadorTest extends PostgresIntegrationTest {

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
        navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
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
        SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");

        pagina = navegador.newContext().newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(e -> erroresDeConsola.add("excepcion sin capturar: " + e));

        pagina.navigate(url("/login"));
        pagina.fill("#email", "yo@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    @AfterEach
    void cerrarPagina() {
        pagina.context().close();
        assertThat(erroresDeConsola).as("errores de consola").isEmpty();
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void pegar(String texto, String etiquetaColor) {
        pagina.fill("#nuevo-recordatorio", texto);
        if (etiquetaColor != null) {
            pagina.getByLabel(etiquetaColor, new Page.GetByLabelOptions().setExact(true)).check();
        }
        pagina.click("li.nota-nueva button[type=submit]");
        pagina.locator("li.nota:not(.nota-nueva)",
                new Page.LocatorOptions().setHasText(texto)).waitFor();
    }

    private int pegadas() {
        return jdbc.sql("SELECT count(*) FROM sticky_note WHERE removed_at IS NULL")
                .query(Integer.class).single();
    }

    @Test
    @DisplayName("el color se elige con el teclado o el raton y pinta la nota")
    void elegirColor() {
        pegar("Nota celeste", "Celeste");

        assertThat(pagina.locator("li.nota.nota-celeste").textContent()).contains("Nota celeste");
        assertThat(jdbc.sql("SELECT color FROM sticky_note").query(String.class).single())
                .isEqualTo("BLUE");
    }

    @Test
    @DisplayName("cancelar el dialogo deja la nota y devuelve el foco al boton")
    void cancelarNoQuita() {
        pegar("Llamar al notario", null);

        pagina.click("li.nota:not(.nota-nueva) button");
        var dialogo = pagina.locator("#dialogo-confirmar");
        assertThat(dialogo.isVisible()).isTrue();
        assertThat(dialogo.textContent()).contains("Llamar al notario");
        // Lo seguro va primero: el foco arranca en Cancelar.
        assertThat(pagina.evaluate("document.activeElement.value")).isEqualTo("cancelar");

        pagina.click("#dialogo-confirmar button[value=cancelar]");

        assertThat(dialogo.isVisible()).isFalse();
        assertThat(pegadas()).isEqualTo(1);
        // El evento close llega en la siguiente vuelta: se espera, no se supone.
        pagina.waitForFunction(
                "document.activeElement.closest('form')?.dataset.confirmar === 'Llamar al notario'");
    }

    @Test
    @DisplayName("Escape tambien cancela")
    void escapeCancela() {
        pegar("Recoger cedulas", null);

        pagina.click("li.nota:not(.nota-nueva) button");
        pagina.keyboard().press("Escape");

        assertThat(pagina.locator("#dialogo-confirmar").isVisible()).isFalse();
        assertThat(pegadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirmar quita la nota del panel")
    void confirmarQuita() {
        pegar("Pedir informe a Contabilidad", "Rosa");

        pagina.click("li.nota:not(.nota-nueva) button");
        pagina.click("#dialogo-confirmar button[value=confirmar]");
        pagina.locator("li.nota:not(.nota-nueva)").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));

        assertThat(pagina.locator("li.nota:not(.nota-nueva)").count()).isZero();
        assertThat(pegadas()).isZero();
    }
}
