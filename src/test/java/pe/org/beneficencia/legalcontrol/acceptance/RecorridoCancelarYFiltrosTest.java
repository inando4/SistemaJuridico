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

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * El recorrido de quickstart.md de la 009, pulsando.
 *
 * <p>Los tres pasos que mas importan son los <b>fallos silenciosos</b>: la accion desde
 * una fila que devuelve a la pagina 0 y obliga a recomponer los filtros (paso 4), la
 * cascada que retiraria los pendientes de otras personas al ocultar un expediente
 * (paso 8) y la cuenta desactivada que desaparece del desplegable y vuelve su trabajo
 * inencontrable (paso 11). Ninguno de los tres da error por su cuenta.
 *
 * <p>Todos los datos son inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoCancelarYFiltrosTest extends PostgresIntegrationTest {

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;
    private final List<String> erroresDeConsola = new ArrayList<>();

    private UUID yo;
    private UUID laOtra;
    private UUID expediente;

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
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        laOtra = SesionDePrueba.crearCuenta(jdbc, encoder, "colega@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Colega Inventada' WHERE id = :id")
                .param("id", laOtra).update();
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        expediente = judicial(yo, "EXP-NUEVEA-2026");

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(erroresDeConsola::add);
    }

    private UUID judicial(UUID owner, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", owner).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private UUID pendiente(UUID owner, String titulo, UUID j) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :hoy, true, :ts, :ts, 1)
                """)
                .param("id", id).param("o", owner).param("t", titulo).param("j", j)
                .param("hoy", LocalDate.now()).param("ts", ahora).update();
        return id;
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void entrar(String correo) {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        pagina.fill("#email", correo);
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    /** La fila de la tabla que contiene ese titulo. */
    private com.microsoft.playwright.Locator fila(String titulo) {
        return pagina.locator("tbody tr").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText(titulo));
    }

    @Test
    @DisplayName("pasos 1 a 3: cancelar, encontrarlo entre los ocultos, devolverlo y su rastro")
    void cancelarEncontrarYDevolver() {
        UUID elMio = pendiente(yo, "Registrado por equivocacion", expediente);
        entrar("abogado@ejemplo.test");

        // Paso 1
        pagina.navigate(url("/pendientes/" + elMio));
        pagina.locator("button:has-text('Cancelar')").click();
        pagina.waitForURL(u -> u.contains("/pendientes"));

        pagina.navigate(url("/pendientes"));
        assertThat(pagina.content())
                .as("paso 1: deja de estorbar en la lista de trabajo")
                .doesNotContain("Registrado por equivocacion");

        // Paso 2: sigue existiendo y vuelve
        pagina.navigate(url("/pendientes?visibility=inactive"));
        assertThat(pagina.content())
                .as("paso 2: cancelar es una correccion, no una condena")
                .contains("Registrado por equivocacion");

        fila("Registrado por equivocacion").locator("button:has-text('Devolver')").click();
        pagina.waitForURL(u -> u.contains("/pendientes"));

        pagina.navigate(url("/pendientes"));
        assertThat(pagina.content()).contains("Registrado por equivocacion");

        // Paso 3: las dos entradas del historial
        pagina.navigate(url("/pendientes/" + elMio + "/historial"));
        String historial = pagina.content();
        assertThat(historial)
                .as("paso 3: nada se retira sin dejar rastro")
                .contains("CANCEL")
                .contains("RESTORE");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 4: actuar desde una fila conserva pagina, filtro y orden")
    void laAccionDesdeLaFilaNoPierdeElSitio() {
        // Es el fallo silencioso: sin queryActual, esto devolveria a la pagina 0 sin
        // dar ningun error y habria que recomponer los filtros en cada gesto.
        for (int i = 0; i < 30; i++) {
            pendiente(yo, String.format("Escrito %02d", i), expediente);
        }
        entrar("abogado@ejemplo.test");

        pagina.navigate(url("/pendientes?sort=title&page=1"));
        String titulo = fila("Escrito 2").first().locator("td").first().textContent().trim();

        fila(titulo).locator("button:has-text('Cumplido')").click();
        pagina.waitForURL(u -> u.contains("/pendientes"));

        assertThat(pagina.url())
                .as("paso 4: la misma pagina y el mismo orden")
                .contains("sort=title")
                .contains("page=1");
    }

    @Test
    @DisplayName("paso 6: a cada persona se le ofrecen solo sus acciones")
    void cadaCualVeLoSuyo() {
        pendiente(laOtra, "Trabajo de la colega", expediente);
        entrar("abogado@ejemplo.test");

        pagina.navigate(url("/pendientes?visibility=all"));
        String suFila = fila("Trabajo de la colega").textContent();

        assertThat(suFila)
                .as("paso 6: leer es compartido")
                .contains("Ver")
                .as("pero no se ofrece lo que el servidor va a rechazar")
                .doesNotContain("Cumplido")
                .doesNotContain("Cancelar");

        // La jefa si.
        pagina.navigate(url("/logout"));
        entrar("jefa@ejemplo.test");
        pagina.navigate(url("/pendientes?visibility=all"));

        assertThat(fila("Trabajo de la colega").textContent()).contains("Cancelar");
    }

    @Test
    @DisplayName("paso 8: ocultar un expediente NO retira los pendientes de otras personas")
    void ocultarNoArrastraElTrabajoAjeno() {
        // El segundo fallo silencioso. Una cascada dejaria sin trabajo a otra persona
        // sin que nadie lo hubiera decidido, y sin dar ningun aviso.
        pendiente(laOtra, "Actuacion de la colega", expediente);
        pendiente(yo, "Actuacion propia", expediente);
        entrar("abogado@ejemplo.test");

        pagina.navigate(url("/judiciales/" + expediente));
        pagina.locator("button:has-text('Ocultar del listado')").click();
        pagina.waitForURL(u -> u.contains("/judiciales/"));

        pagina.navigate(url("/pendientes?visibility=all"));
        assertThat(pagina.content())
                .as("paso 8: los pendientes siguen en la lista de trabajo de quien los tenga")
                .contains("Actuacion de la colega")
                .contains("Actuacion propia");

        // Y el expediente se recupera.
        pagina.navigate(url("/judiciales/" + expediente));
        pagina.locator("button:has-text('Volver a mostrar en el listado')").click();
        pagina.waitForURL(u -> u.contains("/judiciales/"));

        pagina.navigate(url("/judiciales"));
        assertThat(pagina.content()).contains("EXP-NUEVEA-2026");
    }

    @Test
    @DisplayName("paso 10: los filtros de la seccion 27 se componen desde la pantalla")
    void losFiltrosSeComponenPulsando() {
        judicial(laOtra, "EXP-DE-LA-COLEGA-2026");
        entrar("abogado@ejemplo.test");

        pagina.navigate(url("/judiciales"));
        pagina.selectOption("#ownerId", new com.microsoft.playwright.options.SelectOption()
                .setLabel("Colega Inventada"));
        pagina.locator("form button:has-text('Aplicar filtros')").click();
        pagina.waitForURL(u -> u.contains("ownerId="));

        assertThat(pagina.content())
                .as("paso 10: se filtra sin escribir la direccion a mano")
                .contains("EXP-DE-LA-COLEGA-2026")
                .doesNotContain("EXP-NUEVEA-2026");

        // Y hay salida.
        pagina.locator("a:has-text('Quitar filtros')").click();
        pagina.waitForURL(u -> !u.contains("ownerId="));
        assertThat(pagina.content()).contains("EXP-NUEVEA-2026");
    }

    @Test
    @DisplayName("paso 11: una cuenta desactivada sigue en el desplegable y su trabajo se halla")
    void laCuentaDesactivadaSigueBuscable() {
        // El tercer fallo silencioso. Con DestinosDeAsignacion.activos, esta persona
        // desapareceria del desplegable y su expediente pareceria de nadie.
        judicial(laOtra, "EXP-DE-QUIEN-SALIO-2026");
        jdbc.sql("UPDATE app_user SET status = 'INACTIVE' WHERE id = :id")
                .param("id", laOtra).update();
        entrar("abogado@ejemplo.test");

        pagina.navigate(url("/judiciales"));

        assertThat(pagina.locator("#ownerId").textContent())
                .as("paso 11: sigue ofreciendose, marcada")
                .contains("Colega Inventada (desactivada)");

        pagina.selectOption("#ownerId", new com.microsoft.playwright.options.SelectOption()
                .setLabel("Colega Inventada (desactivada)"));
        pagina.locator("form button:has-text('Aplicar filtros')").click();
        pagina.waitForURL(u -> u.contains("ownerId="));

        assertThat(pagina.content())
                .as("y su trabajo se encuentra")
                .contains("EXP-DE-QUIEN-SALIO-2026");
    }

    @Test
    @DisplayName("paso 9: el estado «Archivado» no oculta el procedimiento")
    void archivadoNoEsOcultar() {
        entrar("abogado@ejemplo.test");
        pagina.navigate(url("/judiciales/" + expediente));

        assertThat(pagina.content())
                .as("paso 9: son dos ejes distintos, y la ficha lo dice")
                .contains("Ocultar del listado");
        assertThat(pagina.content())
                .as("y el expediente sigue visible: nadie lo ha ocultado")
                .contains("EXP-NUEVEA-2026");
    }
}
