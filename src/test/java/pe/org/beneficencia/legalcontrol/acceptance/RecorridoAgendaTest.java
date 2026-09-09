package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

import pe.org.beneficencia.legalcontrol.config.ClockConfig;
import pe.org.beneficencia.legalcontrol.integration.DatosSinteticos;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * El recorrido de {@code specs/006-calendario-actividad-busqueda/quickstart.md} con un
 * navegador real.
 *
 * <p>Lo que aporta frente a MockMvc: aqui se <b>pulsa</b> y se lee lo que ve una
 * persona. En la 003 y en la 005, operaciones que funcionaban en la base parecian no
 * hacer nada porque ninguna plantilla renderizaba su mensaje, y eso solo lo encontro el
 * navegador. En esta misma feature, la rejilla del mes se quedo sin eventos por una
 * precedencia de Thymeleaf que compilaba y no fallaba en ninguna capa.
 *
 * <p>Recoge tambien los errores de consola: una expresion de plantilla mal escrita se
 * ve ahi antes que en produccion.
 *
 * <p>Todos los datos son inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoAgendaTest extends PostgresIntegrationTest {

    private static final String TERMINO = "servidumbre";

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;
    private final List<String> erroresDeConsola = new ArrayList<>();

    private UUID abogadaA;
    private UUID abogadoB;
    private LocalDate hoy;

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
        hoy = LocalDate.now(ClockConfig.ZONA);

        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "ana@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "beto@ejemplo.test", "LAWYER");
        DatosSinteticos.sembrarCalendario(jdbc, jefa, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);

        // Cada registro lleva el termino en UN solo campo (pasos 1 y 2 del recorrido).
        judicial("EXP-MAT-2026", "Servidumbre de paso", null, true);
        judicial("EXP-OBS-2026", "Desalojo", "Coordinado por servidumbre vecinal", true);
        judicial("EXP-ARC-2026", "Servidumbre antigua", null, false);
        judicial("EXP-100-2026", "Cobro del 100% del saldo", null, true);
        administrativo("ADM-OBS-2026", "Consulta sobre servidumbre");
        pendienteConNotas("Revisar convenio", "Depende de la servidumbre del predio");

        // Un cumplido a las 23:50, hora de Lima (paso 5).
        cumplido("Escrito presentado de noche", hoy, LocalTime.of(23, 50));
        cumplido("Revision de convenio", hoy, LocalTime.of(10, 0));

        // Eventos del calendario (pasos 11 a 14).
        UUID tipoAudiencia = tipo("Audiencia");
        programado("Audiencia de conciliacion", hoy.plusDays(2), tipoAudiencia);
        conPlazo("Escrito con plazo", hoy.plusDays(3));

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(erroresDeConsola::add);
    }

    // --- fixtures -----------------------------------------------------------

    private void judicial(String numero, String materia, String notas, boolean activo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject, notes,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :materia, :notas, :activo, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogadaA)
                .param("numero", numero).param("materia", materia).param("notas", notas)
                .param("activo", activo).param("ahora", ahora).update();
    }

    private void administrativo(String numero, String notas) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, requesting_area,
                                                      notes, active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, 'Contabilidad', :notas, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogadaA)
                .param("numero", numero).param("notas", notas).param("ahora", ahora).update();
    }

    private void pendienteConNotas(String titulo, String notas) {
        insertarPendiente(titulo, notas, null, null, null, null);
    }

    private void programado(String titulo, LocalDate programada, UUID tipo) {
        insertarPendiente(titulo, null, programada, null, null, tipo);
    }

    private void conPlazo(String titulo, LocalDate limite) {
        insertarPendiente(titulo, null, null, limite, null, null);
    }

    private void cumplido(String titulo, LocalDate dia, LocalTime hora) {
        insertarPendiente(titulo, null, null, null,
                dia.atTime(hora).atZone(ClockConfig.ZONA).toInstant(), null);
    }

    private void insertarPendiente(String titulo, String notas, LocalDate programada,
                                   LocalDate limite, Instant completado, UUID tipo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, notes, pending_task_type_id,
                                          registered_at, scheduled_for, deadline, completed_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :notas, :tipo, :registro, :programada, :limite,
                        :completado, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogadaA).param("titulo", titulo)
                .param("notas", notas).param("tipo", tipo).param("registro", hoy)
                .param("programada", programada).param("limite", limite)
                .param("completado", completado == null ? null : Timestamp.from(completado))
                .param("ahora", ahora).update();
    }

    private UUID tipo(String nombre) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task_type (id, name, enabled, created_by,
                                               created_at, updated_at, version)
                VALUES (:id, :nombre, true, :usuario, :ahora, :ahora, 1)
                """)
                .param("id", id).param("nombre", nombre).param("usuario", abogadaA)
                .param("ahora", ahora).update();
        return id;
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void entrarComo(String correo) {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        // Esperar a que el campo exista no basta: el navegador aplica el autofocus
        // despues, y escribir en ese hueco manda el texto fuera del campo.
        pagina.waitForFunction(
                "() => document.activeElement && document.activeElement.id === 'email'");
        pagina.fill("#email", correo);
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    // --- recorrido ----------------------------------------------------------

    @Test
    @DisplayName("pasos 1 a 4: el buscador encuentra por los campos que faltaban")
    void buscador() {
        entrarComo("ana@ejemplo.test");

        // Paso 1: se escribe en el buscador y se pulsa.
        pagina.navigate(url("/buscar"));
        pagina.locator("#q").waitFor();
        pagina.fill("#q", TERMINO);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("q=" + TERMINO));

        String html = pagina.content();
        assertThat(html).contains("EXP-MAT-2026").contains("EXP-OBS-2026")
                .contains("ADM-OBS-2026").contains("Revisar convenio");
        assertThat(html).as("el archivado sale señalado").contains("EXP-ARC-2026")
                .contains("archivado");

        // Paso 2: el listado coincide en los activos y difiere en los archivados.
        pagina.navigate(url("/judiciales?q=" + TERMINO));
        String listado = pagina.content();
        assertThat(listado).contains("EXP-MAT-2026").contains("EXP-OBS-2026");
        assertThat(listado).doesNotContain("EXP-ARC-2026");

        pagina.navigate(url("/judiciales?q=" + TERMINO + "&visibility=all"));
        assertThat(pagina.content()).contains("EXP-ARC-2026");

        // Paso 3: un termino corto avisa y no consulta.
        pagina.navigate(url("/buscar?q=de"));
        assertThat(pagina.content()).contains("Escriba al menos")
                .doesNotContain("EXP-MAT-2026");

        // Paso 4: los comodines son texto literal.
        pagina.navigate(url("/buscar?q=100%25"));
        assertThat(pagina.content()).contains("EXP-100-2026").doesNotContain("EXP-MAT-2026");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("pasos 5 a 10: la actividad del dia sale sola y se completa a mano")
    void actividadDiaria() {
        entrarComo("ana@ejemplo.test");

        // Paso 5: lo cumplido sale solo, incluido el de las 23:50.
        pagina.navigate(url("/actividad-diaria"));
        assertThat(pagina.content())
                .as("con CAST a date, el de las 23:50 se iria al dia siguiente")
                .contains("Escrito presentado de noche").contains("Revision de convenio");

        // Paso 6: se agrega una actividad manual con tipo escrito a mano.
        pagina.locator("#description").waitFor();
        pagina.fill("#description", "Reunion de coordinacion");
        pagina.fill("#otherType", "Reunion con Contabilidad");
        // Por el texto del boton, no por el action: en esta pantalla hay dos formularios
        // con el mismo action —el filtro por GET y el alta por POST—, y un selector por
        // action los alcanza los dos.
        pagina.locator("button:has-text('Agregar actividad')").click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));

        String html = pagina.content();
        assertThat(html).as("el mensaje se renderiza: la operacion no puede parecer inerte")
                .contains("Actividad registrada");
        assertThat(html).contains("Reunion de coordinacion")
                .contains("Reunion con Contabilidad")
                .contains("registrada a mano");

        // El tipo escrito NO amplia el catalogo.
        Integer enCatalogo = jdbc.sql("""
                SELECT count(*) FROM pending_task_type WHERE name = 'Reunion con Contabilidad'
                """).query(Integer.class).single();
        assertThat(enCatalogo).isZero();

        // Paso 8: retirar no borra.
        pagina.locator("form[action*='/retirar'] button[type=submit]").first().click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));
        assertThat(pagina.content()).contains("Sigue en el historial")
                .doesNotContain("Reunion de coordinacion");

        Integer sigueLaFila = jdbc.sql("SELECT count(*) FROM manual_activity")
                .query(Integer.class).single();
        assertThat(sigueLaFila).as("retirar es active = false, no un DELETE").isEqualTo(1);

        // Paso 15: se ve la actividad de otra persona, sin poder registrarle nada.
        pagina.navigate(url("/actividad-diaria?ownerId=" + abogadoB));
        assertThat(pagina.content()).doesNotContain("Agregar actividad manual");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("pasos 11 a 14: el calendario muestra sus origenes en las tres vistas")
    void calendario() {
        entrarComo("ana@ejemplo.test");

        // Paso 11: la rejilla del mes trae los eventos. Es el paso que encontro la
        // precedencia de th:with contra th:if, que ninguna capa anterior detecto.
        pagina.navigate(url("/calendario?vista=mes"));
        String mes = pagina.content();
        assertThat(mes).contains("Audiencia de conciliacion").contains("Escrito con plazo");
        assertThat(mes).as("una audiencia se ve por su tipo").contains("Audiencia");
        assertThat(mes).as("la rejilla se pinta").contains("Lun").contains("Dom");

        // Paso 12: cambiar de vista no inventa ni pierde eventos de su rango.
        pagina.navigate(url("/calendario?vista=dia&ancla=" + hoy.plusDays(2)));
        assertThat(pagina.content()).contains("Audiencia de conciliacion");

        pagina.navigate(url("/calendario?vista=semana&ancla=" + hoy.plusDays(2)));
        assertThat(pagina.content()).contains("Audiencia de conciliacion");

        // Paso 13: sin el año confirmado el calendario sigue; solo falta el sombreado.
        jdbc.sql("DELETE FROM calendar_review WHERE year = :ano")
                .param("ano", hoy.getYear()).update();
        pagina.navigate(url("/calendario?vista=mes"));
        String sinCalendario = pagina.content();
        assertThat(sinCalendario).contains("Faltan días no laborables por revisar");
        assertThat(sinCalendario)
                .as("los eventos son fechas guardadas, no cuentas de dias habiles")
                .contains("Audiencia de conciliacion");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("las tres pantallas nuevas se alcanzan desde la navegacion")
    void navegacionLlegaALasTres() {
        entrarComo("ana@ejemplo.test");
        pagina.navigate(url("/"));

        for (String enlace : List.of("Buscar", "Actividad diaria", "Calendario")) {
            assertThat(pagina.locator("nav a", new Page.LocatorOptions()).count())
                    .as("la navegacion tiene enlaces").isPositive();
            assertThat(pagina.content())
                    .as("%s se alcanza desde cualquier pantalla", enlace)
                    .contains(enlace);
        }

        assertThat(erroresDeConsola).isEmpty();
    }
}
