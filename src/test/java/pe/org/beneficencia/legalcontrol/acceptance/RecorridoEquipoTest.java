package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

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
 * El recorrido de {@code specs/005-equipo-asignacion/quickstart.md} con un
 * navegador real.
 *
 * <p>Lo que aporta frente a las pruebas de MockMvc: aqui se <b>pulsa</b> lo que ve
 * una persona y se comprueba ademas el estado en la base. Una pantalla puede pintar
 * «reasignado» y dejar la mitad de los pendientes atras; lo que queda cuando nadie
 * mira es la fila.
 *
 * <p>Recoge tambien los errores de consola del navegador: una plantilla rota, un
 * recurso que no carga o una expresion de Thymeleaf mal escrita se ven ahi antes
 * que en produccion.
 *
 * <p>Todos los datos son inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoEquipoTest extends PostgresIntegrationTest {

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;
    private final List<String> erroresDeConsola = new ArrayList<>();

    private UUID jefaId;
    private UUID abogadaA;
    private UUID abogadoB;
    private UUID abogadaC;
    private UUID expediente;
    private UUID pendienteDeC;
    private UUID sueltoDeA;

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
        LocalDate hoy = LocalDate.now();

        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "ana@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "beto@ejemplo.test", "LAWYER");
        abogadaC = SesionDePrueba.crearCuenta(jdbc, encoder, "carla@ejemplo.test", "LAWYER");
        DatosSinteticos.sembrarCalendario(jdbc, jefaId, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);

        // Un expediente de A con tres activos y dos cumplidos.
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 3, 2);
        expediente = ids.get(0);
        // Y uno de C colgado del mismo expediente: el caso del tercero.
        pendienteDeC = DatosSinteticos.pendienteDeExpediente(jdbc, abogadaC, expediente,
                "Informe de Carla", false);

        var ahora = java.sql.Timestamp.from(java.time.Instant.now());
        UUID tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type",
                "Tipo " + UUID.randomUUID(), 1, abogadaA, ahora).get(0);
        UUID prio = DatosSinteticos.sembrarCatalogo(jdbc, "priority",
                "Prioridad " + UUID.randomUUID(), 1, abogadaA, ahora).get(0);
        UUID est = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status",
                "Estado " + UUID.randomUUID(), 1, abogadaA, ahora).get(0);
        sueltoDeA = DatosSinteticos.pendienteSuelto(jdbc, abogadaA, tipo, prio, est,
                "Consulta suelta", hoy.plusDays(4), hoy.minusDays(3));

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
        pagina.fill("#email", correo);
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    private UUID responsableDe(String tabla, UUID id) {
        return jdbc.sql("SELECT owner_id FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(UUID.class).single();
    }

    // ------------------------------------------------------- pasos 1 a 3

    @Test
    @DisplayName("pasos 1-3: la vista muestra a todo el equipo, la jefa incluida")
    void laVistaMuestraAlEquipo() {
        entrarComo("jefa@ejemplo.test");
        pagina.locator("nav summary:has-text('Seguimiento')").click();
        pagina.locator("a[href='/equipo']").click();
        pagina.waitForURL(u -> u.contains("/equipo"));

        String texto = pagina.locator("body").innerText();

        assertThat(texto).contains("Carga del equipo", "Semana del");
        assertThat(texto)
                .as("las cuatro personas activas, con o sin carga")
                .contains("Usuario ana@ejemplo.test", "Usuario beto@ejemplo.test",
                        "Usuario carla@ejemplo.test", "Usuario jefa@ejemplo.test");
        assertThat(texto).contains("(jefatura)");
    }

    @Test
    @DisplayName("paso 2: cada numero lleva a su lista y no trae lo de otros")
    void losNumerosLlevanASuLista() {
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/equipo"));

        // El enlace de «Activos» de Ana.
        pagina.locator("a[href*='ownerId=" + abogadaA + "'][href*='visibility=active']")
                .first().click();
        pagina.waitForURL(u -> u.contains("/pendientes"));

        assertThat(pagina.locator("body").innerText())
                .as("el listado tiene que traer lo de Ana")
                .contains("Consulta suelta");
    }

    // ------------------------------------------------------- pasos 5 a 8

    @Test
    @DisplayName("paso 5: el aviso previo nombra a la tercera persona afectada")
    void elAvisoNombraATerceros() {
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/judiciales/" + expediente));

        String texto = pagina.locator("body").innerText();

        assertThat(texto).contains("Cambiar de responsable", "Se traspasarán");
        assertThat(texto)
                .as("quitarle trabajo a Carla no puede pasar en silencio")
                .contains("Usuario carla@ejemplo.test", "acceso de edición");
    }

    @Test
    @DisplayName("pasos 6 y 8: reasignar mueve todo y el historial dice de quien era")
    void reasignarDesdeLaFicha() {
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/judiciales/" + expediente));

        pagina.selectOption("#ownerId", abogadoB.toString());
        pagina.locator("button:has-text('Reasignar')").click();
        pagina.waitForURL(u -> u.contains("/judiciales/" + expediente));

        // Lo que ve la persona...
        assertThat(pagina.locator("body").innerText())
                .contains("Expediente reasignado a", "Se traspasaron 6 pendientes");

        // ...y lo que queda en la base, que es lo que importa cuando nadie mira.
        assertThat(responsableDe("judicial_case", expediente)).isEqualTo(abogadoB);
        Integer atras = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE judicial_case_id = :e AND owner_id <> :b
                """).param("e", expediente).param("b", abogadoB).query(Integer.class).single();
        assertThat(atras)
                .as("los cumplidos y el de Carla tambien viajan")
                .isZero();

        // El historial del pendiente de Carla conserva SU nombre, no el de Ana.
        pagina.navigate(url("/pendientes/" + pendienteDeC + "/historial"));
        assertThat(pagina.locator("body").innerText())
                .contains("Cambio de responsable")
                .contains("Usuario carla@ejemplo.test");
    }

    // ------------------------------------------------------- pasos 9 a 11

    @Test
    @DisplayName("paso 9: el suelto se reasigna solo; el vinculado ofrece su expediente")
    void elSueltoYElVinculado() {
        entrarComo("jefa@ejemplo.test");

        pagina.navigate(url("/pendientes/" + sueltoDeA));
        pagina.selectOption("#ownerId", abogadoB.toString());
        pagina.locator("button:has-text('Reasignar')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/" + sueltoDeA));

        assertThat(pagina.locator("body").innerText()).contains("Pendiente reasignado a");
        assertThat(responsableDe("pending_task", sueltoDeA)).isEqualTo(abogadoB);

        pagina.navigate(url("/pendientes/" + pendienteDeC));
        assertThat(pagina.locator("body").innerText())
                .as("en vez de un hueco sin explicacion, el camino correcto")
                .contains("Se reasigna reasignando el expediente");
    }

    @Test
    @DisplayName("paso 11: un abogado no ve el formulario de reasignacion")
    void elAbogadoNoLoVe() {
        entrarComo("ana@ejemplo.test");
        pagina.navigate(url("/judiciales/" + expediente));

        assertThat(pagina.locator("body").innerText()).doesNotContain("Cambiar de responsable");
    }

    // ------------------------------------------------------- errores del navegador

    @Test
    @DisplayName("ninguna pantalla nueva produce errores en el navegador")
    void sinErroresDeConsola() {
        entrarComo("jefa@ejemplo.test");

        for (String ruta : List.of("/equipo", "/judiciales/" + expediente,
                "/pendientes/" + sueltoDeA, "/pendientes/" + pendienteDeC,
                "/judiciales/" + expediente + "/historial",
                "/pendientes/" + pendienteDeC + "/historial",
                "/judiciales/nuevo", "/administrativos/nuevo")) {
            pagina.navigate(url(ruta));
            pagina.locator("body").innerText();
        }

        assertThat(erroresDeConsola)
                .as("una plantilla rota o una expresion mal escrita se ve aqui, no en produccion")
                .isEmpty();
    }
}
