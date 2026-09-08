package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
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
 * El recorrido de {@code specs/003-control-pendientes/quickstart.md}, conducido
 * por un navegador real.
 *
 * <p>Sustituye al recorrido a mano (T059). Lo que aporta frente a las pruebas de
 * MockMvc: aqui se pulsan los botones que ve una persona, y cada paso comprueba
 * <b>ademas</b> el estado en la base. Una pantalla puede pintar «cumplido» con la
 * fila mal escrita; el historial es lo que queda cuando nadie mira.
 *
 * <p>Todos los datos son inventados. Los dias no laborables tampoco son los
 * oficiales del Peru: son fechas elegidas para que el calculo de dias habiles
 * tenga un caso que comprobar, y nadie debe confundirlas con el calendario real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoQuickstartTest extends PostgresIntegrationTest {

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort private int puerto;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private Page pagina;
    private UUID usuario;
    private UUID tipo;
    private UUID prioridad;
    private UUID estado;
    private UUID expedienteJudicial;
    private UUID expedienteAdministrativo;

    /**
     * Un viernes cuyo lunes siguiente es laborable, y otro cuyo lunes cae en el
     * dia no laborable sintetico. Fijos y en un ano cubierto: sin esto el
     * resultado dependeria de que dia se ejecute la prueba.
     */
    private static final LocalDate VIERNES_CON_LUNES_HABIL = LocalDate.of(2027, 3, 5);
    private static final LocalDate LUNES_HABIL = LocalDate.of(2027, 3, 8);
    private static final LocalDate VIERNES_CON_LUNES_FERIADO = LocalDate.of(2027, 3, 12);
    private static final LocalDate LUNES_NO_LABORABLE = LocalDate.of(2027, 3, 15);
    private static final LocalDate MARTES_SIGUIENTE = LocalDate.of(2027, 3, 16);

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
        usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");

        Timestamp ahora = Timestamp.from(Instant.now());
        tipo = DatosSinteticos.sembrarCatalogo(
                jdbc, "pending_task_type", "Informe", 1, usuario, ahora).get(0);
        prioridad = DatosSinteticos.sembrarCatalogo(
                jdbc, "priority", "Alta", 1, usuario, ahora).get(0);
        estado = DatosSinteticos.sembrarCatalogo(
                jdbc, "pending_task_status", "Pendiente", 1, usuario, ahora).get(0);

        DatosSinteticos.sembrar(jdbc, usuario, 1, 1);
        DatosSinteticos.sembrarAdministrativos(jdbc, usuario, 1, 1);
        expedienteJudicial = jdbc.sql("SELECT id FROM judicial_case LIMIT 1")
                .query(UUID.class).single();
        expedienteAdministrativo = jdbc.sql("SELECT id FROM administrative_procedure LIMIT 1")
                .query(UUID.class).single();

        int ano = LocalDate.now().getYear();
        DatosSinteticos.sembrarCalendario(jdbc, usuario, ano, ano + 1, 2027);

        pagina = navegador.newPage();
        entrar();
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void entrar() {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        pagina.fill("#email", "abogado@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.locator("button[type=submit]").click();
        pagina.waitForURL("**/judiciales**");
    }

    /** Rellena el alta con lo minimo y devuelve el identificador del pendiente. */
    private UUID registrar(String titulo) {
        pagina.navigate(url("/pendientes/nuevo"));
        pagina.locator("#title").waitFor();
        pagina.fill("#title", titulo);
        pagina.locator("button[type=submit]").first().click();
        pagina.waitForURL("**/pendientes/**");
        return idEnLaUrl();
    }

    private UUID idEnLaUrl() {
        String url = pagina.url();
        return UUID.fromString(url.substring(url.lastIndexOf('/') + 1));
    }

    private void programarPara(UUID id, LocalDate dia) {
        jdbc.sql("UPDATE pending_task SET scheduled_for = :dia WHERE id = :id")
                .param("dia", dia).param("id", id).update();
    }

    private LocalDate fechaProgramada(UUID id) {
        return jdbc.sql("SELECT scheduled_for FROM pending_task WHERE id = :id")
                .param("id", id).query(LocalDate.class).single();
    }

    private int entradasDeHistorial(UUID id) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM audit_event
                WHERE entity_type = 'PENDING_TASK' AND entity_id = :id
                """).param("id", id).query(Integer.class).single();
        return total == null ? 0 : total;
    }

    // ---------------------------------------------------------------- pasos 2 a 4

    @Test
    @DisplayName("paso 2: se registra un pendiente indicando solo el titulo")
    void soloConTitulo() {
        UUID id = registrar("Revisar el expediente de la esquina");

        assertThat(pagina.content()).contains("Revisar el expediente de la esquina");
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task WHERE id = :id")
                .param("id", id).query(Integer.class).single())
                .as("el titulo basta: lo demas es opcional")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("paso 3: los vinculados muestran de que expediente cuelgan")
    void vinculadosAExpediente() {
        pagina.navigate(url("/pendientes/nuevo"));
        pagina.locator("#title").waitFor();
        pagina.fill("#title", "Contestar la demanda");
        pagina.selectOption("#judicialCaseId", expedienteJudicial.toString());
        pagina.locator("button[type=submit]").first().click();
        pagina.waitForURL("**/pendientes/**");

        String numero = jdbc.sql("SELECT case_number FROM judicial_case WHERE id = :id")
                .param("id", expedienteJudicial).query(String.class).single();
        assertThat(pagina.content())
                .as("la ficha debe decir de que expediente cuelga")
                .contains(numero);

        pagina.navigate(url("/pendientes/nuevo"));
        pagina.locator("#title").waitFor();
        pagina.fill("#title", "Responder el requerimiento");
        pagina.selectOption("#administrativeProcedureId", expedienteAdministrativo.toString());
        pagina.locator("button[type=submit]").first().click();
        pagina.waitForURL("**/pendientes/**");

        String expediente = jdbc.sql(
                        "SELECT file_number FROM administrative_procedure WHERE id = :id")
                .param("id", expedienteAdministrativo).query(String.class).single();
        assertThat(pagina.content()).contains(expediente);
    }

    @Test
    @DisplayName("paso 4: un pendiente sin vinculo es valido")
    void sinVinculoEsValido() {
        UUID id = registrar("Comprar toner para la impresora");

        assertThat(jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE id = :id AND judicial_case_id IS NULL
                  AND administrative_procedure_id IS NULL
                """).param("id", id).query(Integer.class).single())
                .as("no todo el trabajo del area cuelga de un expediente")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------- pasos 5 y 6

    @Test
    @DisplayName("paso 5: al cumplir sale de la lista activa y entra en cumplidos")
    void cumplirMueveDeLista() {
        UUID id = registrar("Presentar el escrito de apelacion");

        pagina.locator("form[action$='/cumplir'] button").click();
        pagina.waitForURL("**/pendientes/**");

        assertThat(jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", id).query(Timestamp.class).optional())
                .as("la fecha de cumplimiento es el hecho; el estado del catalogo es la etiqueta")
                .isPresent();

        pagina.navigate(url("/pendientes"));
        assertThat(pagina.content()).doesNotContain("Presentar el escrito de apelacion");

        pagina.navigate(url("/cumplidos"));
        assertThat(pagina.content()).contains("Presentar el escrito de apelacion");
    }

    @Test
    @DisplayName("paso 6: revertir sin motivo se rechaza y no deja rastro")
    void revertirSinMotivoSeRechaza() {
        UUID id = registrar("Elevar el informe a gerencia");
        pagina.locator("form[action$='/cumplir'] button").click();
        pagina.waitForURL("**/pendientes/**");
        int historialTrasCumplir = entradasDeHistorial(id);

        // Se salta la validacion del navegador (required) para llegar al servidor:
        // lo que se comprueba es que la regla la sostiene el servidor, no el HTML.
        pagina.evaluate("() => document.querySelector('#motivoReversion').removeAttribute('required')");
        pagina.locator("form[action$='/revertir'] button").click();
        pagina.waitForLoadState();

        assertThat(jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", id).query(Timestamp.class).optional())
                .as("sin motivo no se revierte: la reversion es una correccion y debe explicarse")
                .isPresent();
        assertThat(entradasDeHistorial(id))
                .as("un intento rechazado no escribe historial")
                .isEqualTo(historialTrasCumplir);
    }

    @Test
    @DisplayName("paso 6: revertir con motivo devuelve a activos y deja las dos entradas")
    void revertirConMotivoDejaLasDosEntradas() {
        UUID id = registrar("Coordinar la audiencia con la procuraduria");

        pagina.locator("form[action$='/cumplir'] button").click();
        pagina.waitForURL("**/pendientes/**");

        pagina.fill("#motivoReversion", "Se marco por error: el escrito no llego a presentarse");
        pagina.locator("form[action$='/revertir'] button").click();
        pagina.waitForURL("**/pendientes/**");

        assertThat(jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", id).query(Timestamp.class).optional())
                .as("vuelve a estar activo")
                .isEmpty();

        pagina.navigate(url("/cumplidos"));
        assertThat(pagina.content()).doesNotContain("Coordinar la audiencia con la procuraduria");

        pagina.navigate(url("/pendientes"));
        assertThat(pagina.content()).contains("Coordinar la audiencia con la procuraduria");

        assertThat(entradasDeHistorial(id))
                .as("el cumplimiento y su reversion: ninguno de los dos se borra")
                .isGreaterThanOrEqualTo(2);

        pagina.navigate(url("/pendientes/" + id + "/historial"));
        assertThat(pagina.content()).contains("Se marco por error");
    }

    // ------------------------------------------------------------- pasos 7 y 8

    @Test
    @DisplayName("paso 7: «no cumplido» un viernes pasa al lunes")
    void noCumplidoPasaAlLunes() {
        UUID id = registrar("Remitir la contestacion");
        programarPara(id, VIERNES_CON_LUNES_HABIL);

        pagina.navigate(url("/pendientes/" + id));
        pagina.locator("form[action$='/no-cumplido'] button").click();
        pagina.waitForURL("**/pendientes/**");

        assertThat(fechaProgramada(id))
                .as("el sabado y el domingo no son habiles")
                .isEqualTo(LUNES_HABIL);
    }

    @Test
    @DisplayName("paso 7: si el lunes es feriado, pasa al martes")
    void noCumplidoSaltaElFeriado() {
        // El 15 de cada ano es el dia no laborable sintetico; en 2027 el 15 de
        // marzo cae lunes. Un solo caso probaria que la fecha se mueve, no que se
        // mueve contando dias habiles.
        assertThat(LUNES_NO_LABORABLE.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

        UUID id = registrar("Presentar los alegatos finales");
        programarPara(id, VIERNES_CON_LUNES_FERIADO);

        pagina.navigate(url("/pendientes/" + id));
        pagina.locator("form[action$='/no-cumplido'] button").click();
        pagina.waitForURL("**/pendientes/**");

        assertThat(fechaProgramada(id))
                .as("se salta el dia no laborable en vez de caer en el")
                .isEqualTo(MARTES_SIGUIENTE);
    }

    @Test
    @DisplayName("paso 8: la reprogramacion manual deja dicho de que fecha a cual")
    void reprogramarDejaLasDosFechas() {
        UUID id = registrar("Revisar el convenio con la municipalidad");
        programarPara(id, VIERNES_CON_LUNES_HABIL);

        pagina.navigate(url("/pendientes/" + id));
        pagina.fill("#scheduledFor", "2027-03-19");
        pagina.fill("#motivoReprogramacion", "La contraparte pidio prorroga");
        pagina.locator("form[action$='/reprogramar'] button").click();
        pagina.waitForURL("**/pendientes/**");

        assertThat(fechaProgramada(id)).isEqualTo(LocalDate.of(2027, 3, 19));

        pagina.navigate(url("/pendientes/" + id + "/historial"));
        String historial = pagina.content();
        assertThat(historial)
                .as("una fecha nueva sin la anterior no dice que cambio")
                .contains("2027-03-05")
                .contains("2027-03-19");
    }

    // ------------------------------------------------------------ pasos 9 y 10

    @Test
    @DisplayName("paso 9: sin fecha limite y con mas de quince dias habiles, avisa")
    void avisoDePendienteSinPlazo() {
        UUID id = registrar("Atender la consulta de la gerencia");
        jdbc.sql("""
                UPDATE pending_task
                SET deadline = NULL, received_at = :recepcion
                WHERE id = :id
                """)
                .param("recepcion", LocalDate.now().minusDays(40))
                .param("id", id).update();

        pagina.navigate(url("/pendientes/" + id));

        assertThat(pagina.content())
                .as("un pendiente sin plazo que lleva semanas parado tiene que notarse")
                .containsIgnoringCase("sin plazo");
    }

    @Test
    @DisplayName("paso 10: «hoy» incluye los de hoy y los vencidos que sigan activos")
    void hoyIncluyeLosVencidos() {
        UUID deHoy = registrar("Firmar la resolucion de hoy");
        programarPara(deHoy, LocalDate.now());

        UUID vencido = registrar("Escrito que se paso de fecha");
        programarPara(vencido, LocalDate.now().minusDays(9));

        UUID futuro = registrar("Reunion del mes que viene");
        programarPara(futuro, LocalDate.now().plusDays(30));

        pagina.navigate(url("/pendientes/hoy"));
        String hoy = pagina.content();

        assertThat(hoy).contains("Firmar la resolucion de hoy");
        assertThat(hoy)
                .as("lo vencido no desaparece de la vista: es lo mas urgente")
                .contains("Escrito que se paso de fecha");
        assertThat(hoy).doesNotContain("Reunion del mes que viene");
    }

    // ------------------------------ comprobaciones que no se ven en pantalla

    @Test
    @DisplayName("el vinculo excluyente lo impide la base, no solo el formulario")
    void vinculoExcluyenteEnLaBase() {
        // Por el formulario no se puede llegar aqui: hay que intentarlo contra la
        // base. Si la regla viviera solo en el codigo, cualquier via alternativa
        // —una correccion a mano, un script— podria dejar la fila incoherente.
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO pending_task
                    (id, owner_id, title, pending_task_type_id, priority_id,
                     pending_task_status_id, judicial_case_id, administrative_procedure_id,
                     received_at, registered_at, active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Vinculado a los dos', :tipo, :prioridad, :estado,
                        :judicial, :administrativo, :hoy, :hoy, true, now(), now(), 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", usuario)
                .param("tipo", tipo).param("prioridad", prioridad).param("estado", estado)
                .param("judicial", expedienteJudicial)
                .param("administrativo", expedienteAdministrativo)
                .param("hoy", LocalDate.now())
                .update())
                .hasMessageContaining("pending_task_vinculo_excluyente");
    }

    @Test
    @DisplayName("sin cobertura de calendario avisa y no mueve la fecha")
    void sinCalendarioNoReprogramaAciegas() {
        UUID id = registrar("Pendiente de un ano sin revisar");
        // 2031 no tiene calendario sembrado: no hay a que dia habil saltar.
        LocalDate viernesSinCobertura = LocalDate.of(2031, 3, 7);
        programarPara(id, viernesSinCobertura);

        pagina.navigate(url("/pendientes/" + id));
        pagina.locator("form[action$='/no-cumplido'] button").click();
        pagina.waitForLoadState();

        assertThat(pagina.content())
                .as("un numero inventado es peor que ninguno: parece fiable")
                .containsIgnoringCase("calendario");
        assertThat(fechaProgramada(id))
                .as("el aviso no basta: la fecha no puede haberse movido")
                .isEqualTo(viernesSinCobertura);
    }

    @Test
    @DisplayName("los cinco catalogos son independientes entre si")
    void catalogosIndependientes() {
        Timestamp ahora = Timestamp.from(Instant.now());
        // El mismo nombre en tres catalogos distintos no debe chocar.
        for (String tabla : List.of("pending_task_type", "priority", "pending_task_status")) {
            DatosSinteticos.sembrarCatalogo(jdbc, tabla, "Coincidencia", 1, usuario, ahora);
        }

        for (String tabla : List.of("pending_task_type", "priority", "pending_task_status")) {
            assertThat(jdbc.sql("SELECT count(*) FROM " + tabla + " WHERE name LIKE 'Coincidencia%'")
                    .query(Integer.class).single())
                    .as("%s no debe ver las filas de los otros", tabla)
                    .isEqualTo(1);
        }
    }
}
