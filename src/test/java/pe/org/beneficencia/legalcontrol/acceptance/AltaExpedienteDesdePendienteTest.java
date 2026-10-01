package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/** Recorre el alta desde un pendiente contra PostgreSQL aislado y un navegador real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AltaExpedienteDesdePendienteTest extends PostgresIntegrationTest {

    private static Playwright playwright;
    private static Browser navegador;
    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    private BrowserContext contexto;
    private Page pagina;
    private UUID jefa;
    private UUID abogado;
    private final List<String> errores = new ArrayList<>();

    private record Tipo(String nombre, String campo, String selector, String tabla,
                        String numero, String vinculo, String otroVinculo) {
        static Tipo de(String nombre) {
            return switch (nombre) {
                case "judicial" -> new Tipo(nombre, "caseNumber", "judicialCaseId", "judicial_case",
                        "case_number", "judicial_case_id", "administrative_procedure_id");
                case "administrativo" -> new Tipo(nombre, "fileNumber", "administrativeProcedureId",
                        "administrative_procedure", "file_number", "administrative_procedure_id",
                        "judicial_case_id");
                default -> throw new IllegalArgumentException(nombre);
            };
        }
    }

    @BeforeAll
    static void abrirNavegador() {
        playwright = Playwright.create();
        navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void cerrarNavegador() {
        if (navegador != null) navegador.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        contexto = navegador.newContext();
        pagina = contexto.newPage();
        errores.clear();
        pagina.onPageError(errores::add);
    }

    @AfterEach
    void cerrarPagina() {
        if (contexto != null) contexto.close();
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void nuevoPendiente(Tipo tipo) {
        pagina.navigate(url("/login"));
        pagina.fill("#email", "jefa@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL(url("/"));
        pagina.navigate(url("/pendientes/nuevo"));
        pagina.fill("#title", "Pendiente con " + tipo.nombre());
        pagina.fill("#notes", "Borrador que debe conservarse");
        pagina.check("#vinculo-" + tipo.nombre());
    }

    private void abrirModal(Tipo tipo) {
        pagina.locator("[data-crear-expediente='" + tipo.nombre() + "']").click();
        pagina.locator("#alta-" + tipo.campo()).waitFor();
    }

    private void esperarCierre() {
        pagina.waitForSelector("#modal-expediente",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN));
    }

    private void crearExpediente(Tipo tipo, String numero) {
        abrirModal(tipo);
        pagina.fill("#alta-" + tipo.campo(), numero);
        pagina.selectOption("#alta-ownerId", abogado.toString());
        pagina.locator("#alta-expediente button[type=submit]").click();
        esperarCierre();
    }

    @ParameterizedTest(name = "{0}: crear, vincular y quitar vínculo en modo {1}")
    @CsvSource({"judicial, estandar", "administrativo, estandar",
            "judicial, rendimiento", "administrativo, rendimiento"})
    void creaSeleccionaYVinculaConResponsablesDistintos(String nombre, String modo) {
        Tipo tipo = Tipo.de(nombre);
        contexto.addInitScript("localStorage.setItem('sistema-juridico.presentacion', '" + modo + "');");
        pagina.setViewportSize(modo.equals("rendimiento") ? 390 : 1440, 900);
        nuevoPendiente(tipo);
        abrirModal(tipo);
        String numero = "MODAL-" + nombre + "-2026";
        pagina.fill("#alta-" + tipo.campo(), numero);
        pagina.selectOption("#alta-ownerId", abogado.toString());
        pagina.keyboard().press("Escape");
        esperarCierre();

        abrirModal(tipo);
        assertThat(pagina.locator("#alta-" + tipo.campo()).inputValue()).isEqualTo(numero);
        assertThat(pagina.locator("#alta-ownerId").inputValue()).isEqualTo(abogado.toString());
        if (nombre.equals("administrativo")) {
            assertThat(pagina.locator("#alta-expediente [name=sequenceNumber]").count()).isZero();
        }
        pagina.locator("#alta-expediente button[type=submit]").click();
        esperarCierre();
        UUID expediente = jdbc.sql("SELECT id FROM " + tipo.tabla() + " WHERE " + tipo.numero() + " = :numero")
                .param("numero", numero).query(UUID.class).single();
        assertThat(pagina.locator("#" + tipo.selector()).inputValue()).isEqualTo(expediente.toString());
        assertThat(pagina.locator("#title").inputValue()).isEqualTo("Pendiente con " + nombre);
        assertThat(pagina.locator("#notes").inputValue()).isEqualTo("Borrador que debe conservarse");
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single()).isZero();

        var evento = jdbc.sql("SELECT actor_id, owner_id FROM audit_event WHERE entity_id = :id AND action = 'CREATE'")
                .param("id", expediente).query().singleRow();
        assertThat(evento.get("actor_id")).isEqualTo(jefa);
        assertThat(evento.get("owner_id")).isEqualTo(abogado);
        pagina.locator("#contenido > form button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/pendientes/") && !u.endsWith("/nuevo"));
        var pendiente = jdbc.sql("SELECT id, owner_id, notes, " + tipo.vinculo() + ", "
                + tipo.otroVinculo() + " FROM pending_task").query().singleRow();
        assertThat(pendiente.get("owner_id")).isEqualTo(jefa);
        assertThat(pendiente.get("notes")).isEqualTo("Borrador que debe conservarse");
        assertThat(pendiente.get(tipo.vinculo())).isEqualTo(expediente);
        assertThat(pendiente.get(tipo.otroVinculo())).isNull();

        pagina.navigate(url("/pendientes/" + pendiente.get("id") + "/editar"));
        pagina.check("#vinculo-ninguno");
        pagina.locator("#contenido > form button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/pendientes/") && !u.contains("/editar"));
        assertThat(jdbc.sql("SELECT " + tipo.vinculo() + " FROM pending_task WHERE id = :id")
                .param("id", pendiente.get("id")).query().singleRow().get(tipo.vinculo())).isNull();
        assertThat(errores).isEmpty();
    }

    @ParameterizedTest(name = "{0}: un número duplicado conserva los borradores")
    @ValueSource(strings = {"judicial", "administrativo"})
    void duplicadoNoCreaRegistroYConservaDatos(String nombre) {
        Tipo tipo = Tipo.de(nombre);
        nuevoPendiente(tipo);
        String numero = "DUPLICADO-" + nombre + "-2026";
        crearExpediente(tipo, numero);
        abrirModal(tipo);
        pagina.fill("#alta-" + tipo.campo(), numero);
        pagina.locator("#alta-expediente button[type=submit]").click();
        pagina.locator("[data-errores-expediente]").waitFor();
        assertThat(pagina.locator("[data-lista-errores]").textContent()).contains("Ya existe");
        assertThat(pagina.locator("#alta-" + tipo.campo()).inputValue()).isEqualTo(numero);
        assertThat(jdbc.sql("SELECT count(*) FROM " + tipo.tabla()).query(Integer.class).single()).isEqualTo(1);
        pagina.keyboard().press("Escape");
        esperarCierre();
        assertThat(pagina.locator("#notes").inputValue()).isEqualTo("Borrador que debe conservarse");
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single()).isZero();
        assertThat(errores).isEmpty();
    }
}
