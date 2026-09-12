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

import pe.org.beneficencia.legalcontrol.integration.DatosSinteticos;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Barrido con navegador real buscando fallos que las pruebas dirigidas no ven.
 *
 * <p>Una prueba dirigida comprueba lo que su autor penso comprobar. Esta recorre
 * todas las pantallas y mira lo que sale mal <b>sin haberlo previsto</b>: claves
 * de traduccion sin resolver, expresiones de plantilla que no se evaluaron,
 * errores de JavaScript, enlaces que no llevan a ninguna parte.
 *
 * <p>Existe porque un fallo asi ya se colo una vez: la interfaz mostraba
 * {@code ??acceso.titulo_es_PE??} en lugar de texto en espanol, y ninguna prueba
 * de las que habia lo veia.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "ux.audit", matches = "true")
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RevisionPantallasTest extends PostgresIntegrationTest {

    /** Todo lo que una persona con sesion puede abrir con un GET sin parametros. */
    private static final List<String> PANTALLAS = List.of(
            "/", "/alertas", "/pendientes", "/pendientes/hoy", "/cumplidos",
            "/judiciales", "/administrativos", "/equipo",
            "/pendientes/nuevo", "/judiciales/nuevo",
            "/administrativos/nuevo", "/dias-no-laborables", "/usuarios",
            "/tipos-de-pendiente", "/prioridades", "/estados-de-pendiente");

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
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        LocalDate hoy = LocalDate.now();
        DatosSinteticos.sembrarCalendario(jdbc, jefa, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);
        DatosSinteticos.sembrar(jdbc, jefa, 3, 2);
        DatosSinteticos.sembrarAdministrativos(jdbc, jefa, 3, 2);
        DatosSinteticos.sembrarPendientes(jdbc, jefa, 20, 3);
        sembrarCadaSituacion(jefa, hoy);

        pagina = navegador.newPage();
        erroresDeConsola.clear();
        pagina.onConsoleMessage(m -> {
            if ("error".equals(m.type())) {
                erroresDeConsola.add(m.text());
            }
        });
        pagina.onPageError(e -> erroresDeConsola.add("excepcion sin capturar: " + e));

        entrar();
    }

    /** Un pendiente de cada situacion, para que el panel no salga todo en cero. */
    private void sembrarCadaSituacion(UUID responsable, LocalDate hoy) {
        UUID tipo = jdbc.sql("SELECT id FROM pending_task_type LIMIT 1")
                .query(UUID.class).single();
        UUID prioridad = jdbc.sql("SELECT id FROM priority LIMIT 1").query(UUID.class).single();
        UUID estado = jdbc.sql("SELECT id FROM pending_task_status LIMIT 1")
                .query(UUID.class).single();
        Timestamp ahora = Timestamp.from(Instant.now());

        record Caso(String titulo, LocalDate limite, LocalDate programado, LocalDate recepcion) { }
        List<Caso> casos = List.of(
                new Caso("Revision vencida", hoy.minusDays(4), null, hoy.minusDays(20)),
                new Caso("Revision de hoy", hoy, null, hoy.minusDays(5)),
                new Caso("Revision programada hoy", null, hoy, hoy.minusDays(5)),
                new Caso("Revision proxima", hoy.plusDays(2), null, hoy.minusDays(3)),
                new Caso("Revision antigua sin plazo", null, null, hoy.minusDays(90)));

        for (Caso c : casos) {
            jdbc.sql("""
                    INSERT INTO pending_task
                        (id, owner_id, title, pending_task_type_id, priority_id,
                         pending_task_status_id, received_at, registered_at, scheduled_for,
                         deadline, active, created_at, updated_at, version)
                    VALUES (:id, :owner, :titulo, :tipo, :prioridad, :estado,
                            :recepcion, :recepcion, :programado, :limite, true,
                            :ahora, :ahora, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("owner", responsable)
                    .param("titulo", c.titulo()).param("tipo", tipo)
                    .param("prioridad", prioridad).param("estado", estado)
                    .param("recepcion", c.recepcion()).param("programado", c.programado())
                    .param("limite", c.limite()).param("ahora", ahora)
                    .update();
        }
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void entrar() {
        pagina.navigate(url("/login"));
        pagina.locator("#email").waitFor();
        pagina.fill("#email", "jefa@ejemplo.test");
        pagina.fill("#password", SesionDePrueba.CONTRASENA);
        pagina.click("button[type=submit]");
        pagina.waitForURL(u -> !u.contains("/login"));
    }


    private final java.nio.file.Path evidencia = java.nio.file.Path.of("target/revision-minuciosa");
    private int captura = 0;

    private void observar(String nombre, String rol) throws Exception {
        java.nio.file.Files.createDirectories(evidencia);
        for (int ancho : new int[]{1280, 390}) {
            pagina.setViewportSize(ancho, 800);
            pagina.evaluate("window.scrollTo(0,0)");
            String archivo = String.format("%03d-%s-%s-%d", ++captura, rol, nombre, ancho);
            pagina.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                    .setPath(evidencia.resolve(archivo + ".png")));
            String datos = (String) pagina.evaluate("""
                (archivo) => JSON.stringify({archivo, url: location.pathname+location.search,
                  title: document.title, viewport: innerWidth, width:document.documentElement.scrollWidth,
                  height:document.documentElement.scrollHeight,
                  headings:[...document.querySelectorAll('h1,h2,h3,summary,legend')].map(e=>e.innerText),
                  controls:[...document.querySelectorAll('input:not([type=hidden]),select,textarea,button')]
                    .map(e=>({tag:e.tagName,id:e.id,type:e.type,text:e.labels?.[0]?.innerText||e.innerText,
                      visible:e.checkVisibility(),y:e.getBoundingClientRect().y,width:e.getBoundingClientRect().width})),
                  links:[...document.querySelectorAll('a')].map(e=>({text:e.innerText,href:e.getAttribute('href'),visible:e.checkVisibility()})),
                  text:document.body.innerText,
                  duplicateIds:[...document.querySelectorAll('[id]')].map(e=>e.id).filter((x,i,a)=>a.indexOf(x)!==i),
                  unlabeled:[...document.querySelectorAll('input:not([type=hidden]),select,textarea')]
                    .filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')).map(e=>e.id||e.name),
                  viewportMeta:document.querySelector('meta[name=viewport]')?.content
                })
                """, archivo);
            java.nio.file.Files.writeString(evidencia.resolve("pantallas.jsonl"), datos + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
    }

    private void visitar(String ruta, String nombre, String rol, int status) throws Exception {
        var respuesta = pagina.navigate(url(ruta));
        assertThat(respuesta.status()).as(ruta).isEqualTo(status);
        observar(nombre, rol);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void recorrerTodasLasPantallasYEstados() throws Exception {
        java.nio.file.Files.createDirectories(evidencia);
        java.nio.file.Files.deleteIfExists(evidencia.resolve("pantallas.jsonl"));
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogada@ejemplo.test", "LAWYER");
        String judicial = jdbc.sql("SELECT id FROM judicial_case WHERE active LIMIT 1").query(UUID.class).single().toString();
        String administrativo = jdbc.sql("SELECT id FROM administrative_procedure WHERE active LIMIT 1").query(UUID.class).single().toString();
        String pendiente = jdbc.sql("SELECT id FROM pending_task WHERE active AND completed_at IS NULL LIMIT 1").query(UUID.class).single().toString();
        var rutas = new java.util.LinkedHashMap<String, String>();
        rutas.put("panel", "/"); rutas.put("alertas", "/alertas");
        rutas.put("pendientes", "/pendientes"); rutas.put("hoy", "/pendientes/hoy");
        rutas.put("cumplidos-vacio", "/cumplidos"); rutas.put("equipo", "/equipo");
        rutas.put("agenda-mes", "/calendario"); rutas.put("agenda-semana", "/calendario?vista=semana");
        rutas.put("agenda-dia", "/calendario?vista=dia");
        rutas.put("actividad-vacia", "/actividad-diaria");
        rutas.put("buscar", "/buscar"); rutas.put("buscar-corto", "/buscar?q=ab");
        rutas.put("buscar-resultados", "/buscar?q=sintetico"); rutas.put("buscar-vacio", "/buscar?q=ZZZ-SIN-RESULTADO");
        rutas.put("configuracion", "/configuracion"); rutas.put("feriados", "/dias-no-laborables");
        rutas.put("contrasena", "/cuenta/contrasena");
        for (String catalogo : List.of("tipos-de-pendiente", "prioridades", "estados-de-pendiente", "estados-procesales", "estados-administrativos")) rutas.put(catalogo, "/"+catalogo);
        for (var entidad : java.util.Map.of("judiciales", judicial, "administrativos", administrativo, "pendientes", pendiente).entrySet()) {
            String raiz="/"+entidad.getKey();
            rutas.put(entidad.getKey()+"-lista", raiz);
            rutas.put(entidad.getKey()+"-vacio", raiz+"?q=ZZZ-SIN-RESULTADO");
            rutas.put(entidad.getKey()+"-alta", raiz+"/nuevo");
            rutas.put(entidad.getKey()+"-ficha", raiz+"/"+entidad.getValue());
            rutas.put(entidad.getKey()+"-historial-vacio", raiz+"/"+entidad.getValue()+"/historial");
        }
        for (String rol : List.of("jefa", "abogada")) {
            if (rol.equals("abogada")) {
                pagina.context().clearCookies(); pagina.navigate(url("/login"));
                pagina.fill("#email", "abogada@ejemplo.test"); pagina.fill("#password", SesionDePrueba.CONTRASENA);
                pagina.click("button[type=submit]"); pagina.waitForURL(u -> !u.contains("/login"));
            }
            for (var ruta:rutas.entrySet()) visitar(ruta.getValue(),ruta.getKey(),rol,200);
            for(var entidad:java.util.Map.of("judiciales",judicial,"administrativos",administrativo,"pendientes",pendiente).entrySet())
                visitar("/"+entidad.getKey()+"/"+entidad.getValue()+"/editar", entidad.getKey()+"-editar",rol,rol.equals("jefa")?200:403);
            visitar("/usuarios","usuarios",rol,rol.equals("jefa")?200:403);
            visitar("/usuarios/nuevo","usuarios-alta",rol,rol.equals("jefa")?200:403);
        }
        pagina.context().clearCookies(); entrar();
        // Record changes through the real forms, so histories contain actual events.
        for(var entidad:java.util.Map.of("judiciales",judicial,"administrativos",administrativo,"pendientes",pendiente).entrySet()) {
            pagina.navigate(url("/"+entidad.getKey()+"/"+entidad.getValue()+"/editar"));
            pagina.fill("#notes", "Observación de revisión UX: texto extenso para revisar lectura y recuperación de cambios.");
            pagina.locator("main form button[type=submit]").click();
            observar(entidad.getKey()+"-guardado", "jefa");
            visitar("/"+entidad.getKey()+"/"+entidad.getValue()+"/historial",entidad.getKey()+"-historial", "jefa",200);
        }
        // Conflict from a genuinely stale edit version.
        pagina.navigate(url("/judiciales/"+judicial+"/editar"));
        jdbc.sql("UPDATE judicial_case SET version=version+1 WHERE id=:id").param("id",UUID.fromString(judicial)).update();
        pagina.locator("main form button[type=submit]").click(); observar("error-conflicto", "jefa");
        visitar("/pendientes/00000000-0000-0000-0000-000000000000","error-no-encontrado","jefa",404);
        visitar("/ruta-inexistente","error-ruta-inexistente","jefa",404);
        // Validation with a known duplicate number, preserving input and selection.
        pagina.navigate(url("/judiciales/"+judicial+"/editar"));
        String numero=pagina.locator("#caseNumber").inputValue();
        pagina.navigate(url("/judiciales/nuevo")); pagina.fill("#caseNumber",numero);
        pagina.fill("#claimant","Nombre que debe conservarse");
        pagina.locator("main form button[type=submit]").click(); observar("judicial-validacion", "jefa");
        // Completion exposes the reverse action and the completed/history screens.
        pagina.navigate(url("/pendientes/"+pendiente));
        pagina.locator("button:has-text('Marcar como cumplido')").click(); observar("pendiente-cumplido","jefa");
        visitar("/cumplidos","cumplidos-con-datos","jefa",200);
        pagina.navigate(url("/pendientes/"+pendiente)); pagina.fill("#motivoReversion","Corrección de prueba UX");
        pagina.locator("button:has-text('Revertir cumplimiento')").click(); observar("pendiente-revertido","jefa");
        pagina.locator("main form[action$='/cancelar'] button").click(); observar("pendiente-cancelado","jefa");
        pagina.locator("main form[action$='/devolver'] button").click(); observar("pendiente-devuelto","jefa");
        // Activity create, expand correction, update, history and removal.
        pagina.navigate(url("/actividad-diaria")); pagina.fill("#description","Actividad manual para revisión UX");
        pagina.locator("button:has-text('Agregar actividad')").click(); observar("actividad-con-datos","jefa");
        pagina.locator("main summary:has-text('Corregir')").click(); observar("actividad-corregir","jefa");
        pagina.locator("main details textarea").fill("Actividad manual corregida en revisión UX");
        pagina.locator("button:has-text('Guardar corrección')").click();
        pagina.locator("main a:has-text('Historial')").click(); observar("actividad-historial","jefa");
        pagina.locator("main a").first().click(); pagina.locator("button:has-text('Retirar')").click(); observar("actividad-retirada","jefa");
        // Account validation, code screen and activation in a fresh browser session.
        pagina.navigate(url("/usuarios/nuevo")); pagina.fill("#name","Persona de revisión UX");
        pagina.fill("#email","jefa@ejemplo.test"); pagina.locator("main button[type=submit]").click(); observar("usuarios-validacion","jefa");
        pagina.fill("#name","Persona de revisión UX"); pagina.fill("#email","alta-ux@ejemplo.test");
        pagina.locator("main button[type=submit]").click(); observar("usuarios-codigo","jefa");
        String codigo=pagina.locator("main p[style]").innerText();
        pagina.context().clearCookies();
        visitar("/login","login","publico",200); visitar("/login?error","login-error","publico",200);
        visitar("/login?expirada","login-expirada","publico",200);
        visitar("/acceso/canjear","activar","publico",200);
        pagina.fill("#email","alta-ux@ejemplo.test"); pagina.fill("#code",codigo);
        pagina.fill("#password",SesionDePrueba.CONTRASENA); pagina.fill("#passwordConfirmation","No coincide con la contraseña");
        pagina.locator("main button[type=submit]").click(); observar("activar-validacion","publico");
        pagina.fill("#email","alta-ux@ejemplo.test"); pagina.fill("#code",codigo);
        pagina.fill("#password",SesionDePrueba.CONTRASENA); pagina.fill("#passwordConfirmation",SesionDePrueba.CONTRASENA);
        pagina.locator("main button[type=submit]").click(); observar("activar-exito","publico");
    }
    @Test
    @org.junit.jupiter.api.Order(2)
    void revisarRecuperacionYContexto() throws Exception {
        UUID colega=SesionDePrueba.crearCuenta(jdbc,encoder,"colega-ux@ejemplo.test","LAWYER");
        String judicial=jdbc.sql("SELECT id FROM judicial_case WHERE active LIMIT 1").query(UUID.class).single().toString();
        String administrativo=jdbc.sql("SELECT id FROM administrative_procedure WHERE active LIMIT 1").query(UUID.class).single().toString();
        // Open every primary navigation destination by its actual link.
        for(String ruta:List.of("/", "/pendientes/hoy", "/pendientes", "/buscar", "/alertas", "/calendario",
                "/actividad-diaria", "/cumplidos", "/equipo", "/judiciales", "/administrativos", "/configuracion")) {
            pagina.navigate(url("/"));
            var enlace=pagina.locator("nav[aria-label='Secciones'] a[href='"+ruta+"']");
            if(!enlace.isVisible()) enlace.locator("xpath=ancestor::details/summary").click();
            enlace.click(); assertThat(pagina.url()).endsWith(ruta);
        }
        // No applied filter should disappear merely because Apply was pressed.
        pagina.navigate(url("/")); pagina.locator("main a[href*='alerta=vencidos']").click();
        observar("foco-vencidos-antes","diagnostico");
        pagina.locator("button:has-text('Aplicar filtros')").click(); observar("foco-vencidos-despues","diagnostico");
        for(String ruta:List.of("/judiciales","/administrativos","/pendientes")) {
            pagina.navigate(url(ruta)); pagina.locator("main summary").click();
            observar(ruta.substring(1)+"-filtros-abiertos","diagnostico");
        }
        // Attempt creation for another owner, correct duplicate number and see final owner.
        for(var entidad:java.util.Map.of("judiciales",judicial,"administrativos",administrativo).entrySet()) {
            String ruta="/"+entidad.getKey(); String campo=entidad.getKey().equals("judiciales")?"#caseNumber":"#fileNumber";
            pagina.navigate(url(ruta+"/"+entidad.getValue()+"/editar")); String numero=pagina.locator(campo).inputValue();
            pagina.navigate(url(ruta+"/nuevo")); pagina.selectOption("#ownerId",colega.toString()); pagina.fill(campo,numero);
            pagina.locator("main button[type=submit]").click(); observar(entidad.getKey()+"-responsable-error","diagnostico");
            pagina.fill(campo,"UX-RESPONSABLE-"+entidad.getKey()); pagina.locator("main button[type=submit]").click();
            observar(entidad.getKey()+"-responsable-resultado","diagnostico");
        }
        // Context loss when returning to a list after a detail visit.
        pagina.navigate(url("/pendientes?q=sintetico&sort=title"));
        pagina.locator("main table tbody tr a").first().click();
        pagina.locator("main a:has-text('Volver a pendientes')").click(); observar("volver-lista-filtrada","diagnostico");
        // Invalid edit of a manual activity: where does the correction land?
        pagina.navigate(url("/actividad-diaria")); pagina.fill("#description","Registro original de diagnóstico");
        pagina.locator("button:has-text('Agregar actividad')").click();
        pagina.locator("main summary:has-text('Corregir')").click();
        pagina.locator("main details textarea").fill("Corrección que se debe conservar");
        pagina.locator("main details select").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(1));
        pagina.locator("main details input[name=otherType]").fill("Otro tipo incompatible");
        pagina.locator("button:has-text('Guardar corrección')").click(); observar("actividad-error-edicion","diagnostico");
        // Password validation and discovery from settings.
        pagina.navigate(url("/cuenta/contrasena")); pagina.fill("#currentPassword","clave incorrecta");
        pagina.fill("#password",SesionDePrueba.CONTRASENA); pagina.fill("#passwordConfirmation",SesionDePrueba.CONTRASENA);
        pagina.locator("main button[type=submit]").click(); observar("contrasena-error","diagnostico");
        // Calendar controls and unavailable coverage.
        pagina.navigate(url("/calendario")); pagina.check("#todos"); pagina.locator("main form button").click();
        observar("agenda-area-completa","diagnostico");
        visitar("/dias-no-laborables?year=2040","feriados-sin-revision","diagnostico",200);
    }

}
