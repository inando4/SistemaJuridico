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
 * El recorrido de quickstart.md, pulsando.
 *
 * <p>Los tres pasos que mas importan son los <b>fallos silenciosos</b>: el vinculo a
 * un expediente archivado que se pierde al guardar sin dar error (paso 7), el filtro
 * que desaparece al aplicar otro filtro (paso 9.3) y el identificador inventado que
 * reventaba con un 500 (paso 10). Ninguno de los tres avisa por su cuenta.
 *
 * <p>Todos los datos son inventados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoExpedientePendientesTest extends PostgresIntegrationTest {

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
    private UUID archivado;
    private UUID ajeno;

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

        expediente = judicial(yo, "EXP-RECORRIDO-2026", true);
        archivado = judicial(yo, "EXP-GUARDADO-2026", false);
        ajeno = judicial(laOtra, "EXP-DE-OTRA-2026", true);

        pendiente(yo, "Redactar contestacion", expediente, false, false);
        pendiente(laOtra, "Revisar antecedentes", expediente, false, false);
        pendiente(yo, "Escrito ya presentado", expediente, true, false);
        pendiente(yo, "Diligencia retirada", expediente, false, true);

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(erroresDeConsola::add);
    }

    private UUID judicial(UUID owner, String numero, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, :act, :a, :a, 1)
                """).param("id", id).param("o", owner).param("n", numero).param("act", activo)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private void pendiente(UUID owner, String titulo, UUID j, boolean cumplido,
                           boolean archivadoElPendiente) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id, registered_at,
                                          completed_at, active, created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :hoy, :fin, :vis, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("j", j).param("hoy", LocalDate.now())
                .param("fin", cumplido ? ahora : null)
                .param("vis", !archivadoElPendiente).param("ts", ahora).update();
    }

    /** Un pendiente por hacer, con titulo propio, colgado de un expediente. */
    private void pendienteConTitulo(UUID owner, String titulo, UUID j) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :hoy, true, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("j", j).param("hoy", LocalDate.now()).param("ts", ahora).update();
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
        pagina.waitForURL(u -> !u.contains("/login"));
    }

    @Test
    @DisplayName("pasos 1 a 3: el bloque se ve, con lo de otros y sin lo archivado")
    void elBloqueEnLaFicha() {
        entrar();
        pagina.navigate(url("/judiciales/" + expediente));

        String html = pagina.content();
        assertThat(html)
                .contains("Pendientes relacionados")
                .contains("Redactar contestacion")
                .as("el de la otra abogada se ve, con su nombre")
                .contains("Revisar antecedentes")
                .contains("Colega Inventada")
                .as("lo cumplido es historia del expediente")
                .contains("Escrito ya presentado")
                .as("lo retirado no vuelve")
                .doesNotContain("Diligencia retirada");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 2: marcar cumplido desde la ficha del pendiente y volver al expediente")
    void pasoDosCumplirDesdeLaInterfaz() {
        entrar();
        pagina.navigate(url("/judiciales/" + expediente));

        // El recorrido de verdad: se pulsa en la ficha del pendiente, no se toca la
        // base. Es el unico sitio donde se comprueba que despues de cumplir algo
        // SIGUE en el bloque, que es lo que distingue a la ficha del listado de
        // trabajo, donde lo cumplido desaparece a proposito.
        pagina.locator("a:has-text('Redactar contestacion')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        pagina.locator("button:has-text('Marcar como cumplido')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        pagina.navigate(url("/judiciales/" + expediente));
        String bloque = pagina.locator("section:has(h2:has-text('Pendientes relacionados'))")
                .textContent();

        assertThat(bloque)
                .as("lo cumplido es historia del expediente y se queda a la vista")
                .contains("Redactar contestacion")
                .contains("Cumplido");

        // Y pasa detras de lo que aun queda por hacer.
        assertThat(bloque.indexOf("Redactar contestacion"))
                .as("por hacer primero, cumplidos despues")
                .isGreaterThan(bloque.indexOf("Revisar antecedentes"));

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 3: un expediente sin pendientes conserva el bloque, con su aviso")
    void pasoTresBloqueVacio() {
        UUID vacio = judicial(yo, "EXP-SIN-NADA-2026", true);

        entrar();
        pagina.navigate(url("/judiciales/" + vacio));

        assertThat(pagina.content())
                .as("un hueco en blanco no distingue «no hay» de «no cargo la pantalla»")
                .contains("Pendientes relacionados")
                .contains("no tiene ningún pendiente registrado");
        assertThat(pagina.locator("a:has-text('+ Crear nuevo pendiente relacionado')").count())
                .as("y la salida para registrar el primero sigue ahi")
                .isPositive();
    }

    @Test
    @DisplayName("paso 8: con 26 vinculos la ficha corta en 25 y ofrece «Ver todos»")
    void pasoOchoVerTodosConDesborde() {
        // Los 26 se siembran aqui y no en preparar(): si estuvieran en el fixture
        // comun, cada prueba de la clase pagaria las inserciones.
        UUID cargado = judicial(yo, "EXP-CARGADO-2026", true);
        for (int i = 0; i < 26; i++) {
            pendienteConTitulo(yo, "Actuacion numero " + i, cargado);
        }

        entrar();
        pagina.navigate(url("/judiciales/" + cargado));

        String bloque = pagina.locator("section:has(h2:has-text('Pendientes relacionados'))")
                .textContent();
        int filas = bloque.split("Actuacion numero ", -1).length - 1;

        assertThat(filas)
                .as("25 por pagina; la 26 solo sirvio para saber que habia mas")
                .isEqualTo(25);
        assertThat(bloque).contains("Hay más pendientes de los que caben aquí");

        // Con desborde el enlace es «Ver todos», la otra rama del th:if. Se acota al
        // parrafo para no depender de una coincidencia de subcadena en otra parte.
        pagina.locator("section p:has-text('Hay más pendientes') a:has-text('Ver todos')")
                .click();
        pagina.waitForURL(u -> u.contains("/pendientes?"));

        assertThat(pagina.url()).contains("judicialCaseId=" + cargado);
        assertThat(pagina.content())
                .contains("Pendientes del expediente")
                .contains("EXP-CARGADO-2026");
    }

    @Test
    @DisplayName("pasos 9.1 y 9.2: paginar y ordenar tampoco pierden el expediente")
    void pasoNuevePaginarYOrdenar() {
        UUID cargado = judicial(yo, "EXP-PAGINADO-2026", true);
        for (int i = 0; i < 30; i++) {
            pendienteConTitulo(yo, "Escrito numero " + i, cargado);
        }

        entrar();
        pagina.navigate(url("/pendientes?judicialCaseId=" + cargado));

        // 9.1 — paginar. Este camino es comoQuery(), no el formulario: son dos
        // portadores distintos y hay que comprobar los dos.
        pagina.locator("a:has-text('Página siguiente')").click();
        pagina.waitForURL(u -> u.contains("page=1"));

        assertThat(pagina.url())
                .as("el expediente viaja en el enlace de paginacion")
                .contains("judicialCaseId=" + cargado);
        assertThat(pagina.content()).contains("Pendientes del expediente");

        // 9.2 — ordenar, que pasa por el formulario.
        pagina.selectOption("#sort", "title");
        pagina.locator("form button:has-text('Aplicar filtros')").click();
        pagina.waitForURL(u -> u.contains("sort=title"));

        assertThat(pagina.url()).contains("judicialCaseId=" + cargado);
        assertThat(pagina.content()).doesNotContain("Redactar contestacion");
    }

    @Test
    @DisplayName("paso 4: se crea un pendiente ya vinculado, sin buscar el expediente")
    void altaVinculadaPulsando() {
        entrar();
        pagina.navigate(url("/judiciales/" + expediente));

        pagina.locator("a:has-text('+ Crear nuevo pendiente relacionado')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/nuevo"));

        assertThat(pagina.locator("#judicialCaseId").inputValue())
                .as("llega elegido: no hay que buscarlo en el desplegable")
                .isEqualTo(expediente.toString());

        pagina.fill("#title", "Preparar alegato de cierre");
        pagina.locator("form button:has-text('Guardar')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        pagina.navigate(url("/judiciales/" + expediente));
        assertThat(pagina.content()).contains("Preparar alegato de cierre");
        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 5: el vinculo pre-elegido se puede quitar antes de guardar")
    void elVinculoSePuedeQuitar() {
        entrar();
        pagina.navigate(url("/pendientes/nuevo?judicialCaseId=" + expediente));

        pagina.selectOption("#judicialCaseId", "");
        pagina.fill("#title", "Comprar toner para la impresora");
        pagina.locator("form button:has-text('Guardar')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        UUID vinculo = jdbc.sql("""
                SELECT judicial_case_id FROM pending_task
                WHERE title = 'Comprar toner para la impresora'
                """).query(UUID.class).optional().orElse(null);

        assertThat(vinculo)
                .as("el pre-elegido era una sugerencia, no una imposicion")
                .isNull();
    }

    @Test
    @DisplayName("paso 6: se avisa de quien lleva el expediente antes de guardar")
    void avisoDeExpedienteAjeno() {
        entrar();
        pagina.navigate(url("/pendientes/nuevo?judicialCaseId=" + ajeno));

        assertThat(pagina.content())
                .contains("Colega Inventada")
                .contains("EXP-DE-OTRA-2026");
    }

    @Test
    @DisplayName("paso 7 (fallo silencioso): el vinculo a un expediente archivado se guarda")
    void pasoSieteElArchivadoNoPierdeElVinculo() {
        entrar();
        pagina.navigate(url("/pendientes/nuevo?judicialCaseId=" + archivado));

        // Sin la opcion añadida, el <select> saldria vacio y al guardar el vinculo
        // desapareceria sin ningun error: la pantalla diria «guardado» igual.
        assertThat(pagina.locator("#judicialCaseId").inputValue())
                .as("el archivado esta entre las opciones y viene elegido")
                .isEqualTo(archivado.toString());

        pagina.fill("#title", "Escrito de expediente guardado");
        pagina.locator("form button:has-text('Guardar')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/"));

        UUID vinculo = jdbc.sql("""
                SELECT judicial_case_id FROM pending_task
                WHERE title = 'Escrito de expediente guardado'
                """).query(UUID.class).single();

        assertThat(vinculo)
                .as("el fallo silencioso seria un null aqui, sin nada que lo delatara")
                .isEqualTo(archivado);
    }

    @Test
    @DisplayName("paso 8 sin desborde: el enlace al listado filtrado esta igualmente")
    void pasoOchoSinDesborde() {
        entrar();
        pagina.navigate(url("/judiciales/" + expediente));

        // Con cuatro pendientes no hay aviso de «hay mas», pero si el enlace.
        pagina.locator("a:has-text('Ver estos pendientes en el listado')").click();
        pagina.waitForURL(u -> u.contains("/pendientes?"));

        assertThat(pagina.content())
                .contains("Pendientes del expediente")
                .contains("EXP-RECORRIDO-2026")
                .contains("Redactar contestacion");
    }

    @Test
    @DisplayName("paso 9.3 (fallo silencioso): aplicar otro filtro no borra el expediente")
    void pasoNueveElFiltroSobreviveAlFormulario() {
        entrar();
        pagina.navigate(url("/pendientes?judicialCaseId=" + expediente));

        // Un <form method=\"get\"> envia solo sus campos. Sin el campo oculto, este
        // clic devolveria el listado completo y el usuario veria de golpe el trabajo
        // de todo el mundo sin entender que habia cambiado.
        pagina.selectOption("#visibility", "all");
        pagina.locator("form button:has-text('Aplicar filtros')").click();
        pagina.waitForURL(u -> u.contains("visibility=all"));

        assertThat(pagina.url())
                .as("el expediente sigue en la URL despues de aplicar otro filtro")
                .contains("judicialCaseId=" + expediente);
        assertThat(pagina.content())
                .contains("Redactar contestacion")
                .as("con visibility=all aparece ademas lo archivado de ESTE expediente")
                .contains("Diligencia retirada");
    }

    @Test
    @DisplayName("paso 9.4: se puede salir del filtro")
    void seSaleDelFiltro() {
        entrar();
        pagina.navigate(url("/pendientes?judicialCaseId=" + expediente));

        pagina.locator("p.aviso a:has-text('Ver todos los pendientes')").click();
        pagina.waitForURL(u -> !u.contains("judicialCaseId"));

        assertThat(pagina.content()).doesNotContain("Pendientes del expediente");
    }

    @Test
    @DisplayName("paso 10 (fallo silencioso): un identificador inventado no da error 500")
    void pasoDiezIdentificadorInventado() {
        entrar();
        String inventado = "00000000-0000-0000-0000-000000000000";

        var enElListado = pagina.navigate(url("/pendientes?judicialCaseId=" + inventado));
        assertThat(enElListado.status())
                .as("lista vacia, no una pagina de error")
                .isEqualTo(200);
        assertThat(pagina.content()).doesNotContain("Redactar contestacion");

        var enElAlta = pagina.navigate(url("/pendientes/nuevo?judicialCaseId=" + inventado));
        assertThat(enElAlta.status())
                .as("antes esto llegaba a la clave foranea y salia como 500")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("la ficha administrativa se comporta igual")
    void laFichaAdministrativa() {
        UUID procedimiento = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :o, 'ADM-RECORRIDO-2026', true, :a, :a, 1)
                """).param("id", procedimiento).param("o", yo)
                .param("a", Timestamp.from(Instant.now())).update();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, administrative_procedure_id,
                                          registered_at, active, created_at, updated_at, version)
                VALUES (:id, :o, 'Informe legal solicitado', :p, :hoy, true, :ts, :ts, 1)
                """).param("id", UUID.randomUUID()).param("o", yo).param("p", procedimiento)
                .param("hoy", LocalDate.now()).param("ts", ahora).update();

        entrar();
        pagina.navigate(url("/administrativos/" + procedimiento));

        assertThat(pagina.content())
                .contains("Pendientes relacionados")
                .contains("Informe legal solicitado");

        pagina.locator("a:has-text('+ Crear nuevo pendiente relacionado')").click();
        pagina.waitForURL(u -> u.contains("/pendientes/nuevo"));

        assertThat(pagina.locator("#administrativeProcedureId").inputValue())
                .isEqualTo(procedimiento.toString());
        assertThat(erroresDeConsola).isEmpty();
    }
}
