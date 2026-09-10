package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import com.microsoft.playwright.options.SelectOption;

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Un dia de trabajo completo, de punta a punta y por la interfaz.
 *
 * <p>Los demas recorridos comprueban una funcionalidad cada uno. Este comprueba que
 * <b>encajan entre si</b>: lo que se crea en una pantalla aparece donde tiene que
 * aparecer en las otras seis, y lo que se hace en una se refleja en las demas.
 *
 * <p>Casi nada se siembra en la base: los catalogos, los expedientes y los pendientes
 * se crean pulsando, porque el objetivo es justamente que el camino del usuario
 * funcione entero. Solo las cuentas y el calendario van por SQL, que son requisitos
 * previos y no parte del recorrido.
 *
 * <p>Todos los datos son inventados: ni nombres, ni numeros de expediente, ni feriados
 * reales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoCompletoTest extends PostgresIntegrationTest {

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;
    private final List<String> erroresDeConsola = new ArrayList<>();

    private static final String EXPEDIENTE = "EXP-COMPLETO-2026";
    private static final String PROCEDIMIENTO = "ADM-COMPLETO-2026";
    private static final String PENDIENTE = "Redactar informe de contestacion";
    private static final String SUELTO = "Comprar toner para impresora";

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
        var jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        var abogada = SesionDePrueba.crearCuenta(jdbc, encoder, "abogada@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Jefatura Inventada' WHERE id = :id")
                .param("id", jefa).update();
        jdbc.sql("UPDATE app_user SET name = 'Abogada Inventada' WHERE id = :id")
                .param("id", abogada).update();

        // Requisito previo, no parte del recorrido: sin cobertura de calendario el
        // sistema avisa en vez de contar dias habiles, y eso taparia lo que se mide.
        int ano = LocalDate.now().getYear();
        pe.org.beneficencia.legalcontrol.integration.DatosSinteticos
                .sembrarCalendario(jdbc, jefa, ano - 1, ano, ano + 1);

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(erroresDeConsola::add);
        // Que peticion falla, no solo que fallo alguna: un «404» a secas no dice nada.
        pagina.onResponse(r -> {
            if (r.status() >= 400) {
                erroresDeConsola.add(r.status() + " " + r.url());
            }
        });
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

    /**
     * Cerrar sesion pulsando, no navegando.
     *
     * <p>{@code /logout} es POST a proposito —un GET que cierra sesion se dispara con
     * cualquier enlace o precarga—, asi que navegar a el da 404. Se pulsa el boton, que
     * es lo que hace un usuario.
     */
    private void salir() {
        // Se pasa primero por un listado porque la barra de navegacion —y con ella el
        // boton de salir— solo esta en las doce pantallas de listado, no en las fichas
        // ni en los formularios. Es lo que hace un usuario: volver y salir.
        pagina.navigate(url("/pendientes"));
        pagina.locator("form[action$='/logout'] button[type=submit]").first().click();
        pagina.waitForURL(u -> u.contains("/login"));
    }

    private void crearCatalogo(String ruta, String nombre) {
        pagina.navigate(url(ruta));
        pagina.fill("#name", nombre);
        pagina.locator("form[action$='" + ruta + "'] button[type=submit]").click();
        pagina.waitForURL(u -> u.contains(ruta));
    }

    private com.microsoft.playwright.Locator fila(String texto) {
        return pagina.locator("tbody tr").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText(texto));
    }

    @Test
    @DisplayName("un dia de trabajo entero: crear, ver en todas partes, cumplir y dejar rastro")
    void unDiaDeTrabajoCompleto() {
        // ---------- 1. La jefa prepara los catalogos (funcionalidades 001, 003, 007)
        entrar("jefa@ejemplo.test");

        assertThat(pagina.url()).as("el panel es la pantalla de entrada").doesNotContain("/login");

        pagina.navigate(url("/configuracion"));
        assertThat(pagina.content())
                .as("el centro de configuracion enlaza los catalogos (seccion 36)")
                .contains("Estados procesales")
                .contains("Tipos de pendiente");

        crearCatalogo("/estados-procesales", "En trámite");
        crearCatalogo("/tipos-de-pendiente", "Informe legal");
        crearCatalogo("/prioridades", "Alta");
        crearCatalogo("/estados-de-pendiente", "Por hacer");
        crearCatalogo("/estados-administrativos", "En evaluación");

        // ---------- 2. Se registran los dos tipos de expediente (001 y 002)
        pagina.navigate(url("/judiciales/nuevo"));
        pagina.fill("#caseNumber", EXPEDIENTE);
        pagina.fill("#claimant", "Demandante Inventado");
        pagina.fill("#respondent", "Demandado Inventado");
        pagina.fill("#subject", "Desalojo por ocupacion precaria");
        pagina.selectOption("#proceduralStatusId", new SelectOption().setLabel("En trámite"));
        pagina.fill("#deadline", LocalDate.now().plusDays(20).toString());
        pagina.locator("form button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/judiciales"));

        pagina.navigate(url("/administrativos/nuevo"));
        pagina.fill("#fileNumber", PROCEDIMIENTO);
        pagina.fill("#requestingArea", "Gerencia Inventada");
        pagina.fill("#request", "Opinion legal sobre convenio");
        pagina.locator("form button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/administrativos"));

        assertThat(pagina.content()).contains(PROCEDIMIENTO);

        // ---------- 3. Un pendiente creado DESDE la ficha del expediente (008)
        pagina.navigate(url("/judiciales"));
        pagina.locator("a:has-text('" + EXPEDIENTE + "')").first().click();
        pagina.waitForURL(u -> u.contains("/judiciales/"));
        String rutaFicha = pagina.url();

        assertThat(pagina.content())
                .as("la ficha trae el bloque de la seccion 28, vacio de momento")
                .contains("Pendientes relacionados")
                .contains("no tiene ningún pendiente registrado");

        pagina.locator("a:has-text('+ Crear nuevo pendiente relacionado')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/nuevo"));

        assertThat(pagina.locator("#judicialCaseId").inputValue())
                .as("llega con el expediente ya elegido: no hay que buscarlo")
                .isNotEmpty();

        pagina.fill("#title", PENDIENTE);
        pagina.selectOption("#pendingTaskTypeId", new SelectOption().setLabel("Informe legal"));
        pagina.selectOption("#priorityId", new SelectOption().setLabel("Alta"));
        pagina.fill("#receivedAt", LocalDate.now().toString());
        pagina.fill("#scheduledFor", LocalDate.now().toString());
        pagina.fill("#deadline", LocalDate.now().plusDays(2).toString());
        pagina.locator("form button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        // Y uno suelto, el ejemplo del propio insumo (seccion 9).
        pagina.navigate(url("/pendientes/nuevo"));
        pagina.fill("#title", SUELTO);
        pagina.fill("#receivedAt", LocalDate.now().toString());
        pagina.locator("form button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        // ---------- 4. El vinculo se ve en los dos sentidos (008)
        pagina.navigate(rutaFicha);
        assertThat(pagina.content())
                .as("desde el expediente se llega a su pendiente")
                .contains(PENDIENTE)
                .doesNotContain("no tiene ningún pendiente registrado");

        // ---------- 5. Aparece en las cuatro pantallas de trabajo (003, 004, 006)
        pagina.navigate(url("/"));
        assertThat(pagina.content()).as("panel (seccion 23)").contains("Pendientes");

        pagina.navigate(url("/pendientes/hoy"));
        assertThat(pagina.content()).as("pendientes de hoy (seccion 26)").contains(PENDIENTE);

        pagina.navigate(url("/alertas"));
        assertThat(pagina.content()).as("alertas (seccion 35)").contains(PENDIENTE);

        pagina.navigate(url("/calendario"));
        assertThat(pagina.content()).as("calendario (seccion 31)").contains(PENDIENTE);

        // ---------- 6. El buscador global lo encuentra por texto (006)
        pagina.navigate(url("/buscar"));
        pagina.fill("#q", "contestacion");
        pagina.locator("form[role=search] button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("q="));

        assertThat(pagina.content())
                .as("el buscador de la seccion 34 cruza las tres entidades")
                .contains(PENDIENTE);

        // ---------- 7. Los filtros nuevos, desde la pantalla (009)
        pagina.navigate(url("/pendientes"));
        pagina.selectOption("#priorityId", new SelectOption().setLabel("Alta"));
        pagina.locator("form button:has-text('Aplicar filtros')").click();
        pagina.waitForURL(u -> u.contains("priorityId="));

        assertThat(pagina.content())
                .as("el de prioridad Alta esta; el suelto, que no tiene prioridad, no")
                .contains(PENDIENTE)
                .doesNotContain(SUELTO);

        // ---------- 8. Cumplirlo desde la fila, sin abrirlo (009)
        pagina.navigate(url("/pendientes"));
        fila(PENDIENTE).locator("button:has-text('Cumplido')").click();
        pagina.waitForURL(u -> u.contains("/pendientes"));

        pagina.navigate(url("/pendientes"));
        assertThat(pagina.content())
                .as("lo cumplido sale de la lista de trabajo")
                .doesNotContain(PENDIENTE);

        // ---------- 9. Pero sigue en su sitio: cumplidos, ficha y actividad del dia
        pagina.navigate(url("/cumplidos"));
        assertThat(pagina.content()).as("historial de cumplidas (seccion 32)").contains(PENDIENTE);

        pagina.navigate(rutaFicha);
        assertThat(pagina.content())
                .as("en la ficha del expediente sigue: es su historia")
                .contains(PENDIENTE);

        pagina.navigate(url("/actividad-diaria"));
        assertThat(pagina.content())
                .as("«que hice hoy» lo recoge solo (seccion 33)")
                .contains(PENDIENTE);

        // ---------- 10. Una actividad manual, la mitad que se escribe a mano (006)
        pagina.fill("#description", "Reunion con la Gerencia Inventada");
        pagina.locator("form[action$='/actividad-diaria'] button:has-text('Agregar actividad')")
                .click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));

        assertThat(pagina.content()).contains("Reunion con la Gerencia Inventada");

        // ---------- 11. Cancelar lo que sobra, y que quede rastro (009)
        pagina.navigate(url("/pendientes"));
        fila(SUELTO).locator("button:has-text('Cancelar')").click();
        pagina.waitForURL(u -> u.contains("/pendientes"));

        pagina.navigate(url("/pendientes"));
        assertThat(pagina.content()).doesNotContain(SUELTO);

        pagina.navigate(url("/pendientes?visibility=inactive"));
        assertThat(pagina.content())
                .as("cancelar no borra: sigue ahi si se pide")
                .contains(SUELTO);

        // ---------- 12. La vista de equipo y la reasignacion (005)
        pagina.navigate(url("/equipo"));
        assertThat(pagina.content())
                .as("vista de equipo (seccion 5.2)")
                .contains("Abogada Inventada")
                .contains("Jefatura Inventada");

        pagina.navigate(rutaFicha);
        assertThat(pagina.content())
                .as("la jefa puede reasignar el expediente (seccion 5.3)")
                .contains("Cambiar de responsable");

        pagina.selectOption("#ownerId", new SelectOption().setLabel("Abogada Inventada"));
        pagina.locator("form:has(#ownerId) button:has-text('Reasignar')").click();
        pagina.waitForURL(u -> u.contains("/judiciales/"));

        // ---------- 13. La abogada ve lo que le han traspasado
        salir();
        entrar("abogada@ejemplo.test");

        pagina.navigate(url("/judiciales"));
        assertThat(pagina.content())
                .as("el expediente reasignado aparece bajo su nombre")
                .contains(EXPEDIENTE);

        // Y no se le ofrece actuar sobre el trabajo de la jefa.
        pagina.navigate(url("/pendientes?visibility=all"));
        if (fila(SUELTO).count() > 0) {
            assertThat(fila(SUELTO).textContent())
                    .as("leer es compartido; actuar no")
                    .doesNotContain("Devolver");
        }

        // ---------- 14. Nada de esto ocurrio en silencio (principio VII)
        salir();
        entrar("jefa@ejemplo.test");
        pagina.navigate(rutaFicha);
        pagina.locator("a:has-text('Ver historial')").click();
        pagina.waitForURL(u -> u.contains("/historial"));

        assertThat(pagina.content())
                .as("el traspaso quedo registrado con quien y cuando")
                .containsAnyOf("REASSIGN", "Reasign", "responsable");

        // ---------- 15. Ni una sola pagina rompio por el camino
        assertThat(erroresDeConsola)
                .as("quince pantallas encadenadas sin un solo error de consola")
                .isEmpty();
    }
}
