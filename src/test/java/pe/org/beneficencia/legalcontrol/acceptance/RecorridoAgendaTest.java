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
    private UUID tipoEnUso;
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
        // Paso 14: programacion Y vencimiento, en dos dias distintos del mismo mes.
        conDosFechas("Informe programado y con plazo", hoy.plusDays(4), hoy.plusDays(6));

        // Paso 7: un tipo de catalogo usado SOLO por una actividad manual.
        tipoEnUso = tipo("Informe legal");
        actividadConTipo("Informe legal del mes", tipoEnUso);

        // Paso 9: una actividad de Ana, para ver quien puede corregirla.
        actividadDe(abogadaA, "Parte de trabajo de Ana");

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

    private void conDosFechas(String titulo, LocalDate programada, LocalDate limite) {
        insertarPendiente(titulo, null, programada, limite, null, null);
    }

    private void actividadConTipo(String descripcion, UUID tipo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             pending_task_type_id, created_at, updated_at, version)
                VALUES (:id, :owner, :dia, :desc, :tipo, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogadaA).param("dia", hoy)
                .param("desc", descripcion).param("tipo", tipo).param("ahora", ahora).update();
    }

    private void actividadDe(UUID responsable, String descripcion) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             created_at, updated_at, version)
                VALUES (:id, :owner, :dia, :desc, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", responsable).param("dia", hoy)
                .param("desc", descripcion).param("ahora", ahora).update();
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

        // La correccion existe y llega al servidor (RF-020, RF-021).
        // Acotado a LA actividad, no al tipo de formulario: la pantalla tiene un
        // formulario de correccion por actividad, mas el de alta, todos con los mismos
        // nombres de campo. Elegir por posicion escribe en el que no es, y el sintoma
        // es desconcertante: «Actividad corregida» sale igual, porque el POST se envia
        // con el texto original.
        var fila = pagina.locator("li:has-text('Reunion de coordinacion')");
        fila.locator("summary:has-text('Corregir')").click();
        fila.locator("form[action*='/editar'] textarea[name=description]")
                .fill("Reunion de coordinacion ampliada");
        fila.locator("form[action*='/editar'] button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));
        assertThat(pagina.content()).contains("Actividad corregida")
                .contains("Reunion de coordinacion ampliada");

        // Paso 8: retirar no borra.
        Integer antesDeRetirar = jdbc.sql("SELECT count(*) FROM manual_activity")
                .query(Integer.class).single();

        pagina.locator("li:has-text('Reunion de coordinacion ampliada')")
                .locator("form[action*='/retirar'] button[type=submit]").click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));
        assertThat(pagina.content()).contains("Sigue en el historial")
                .doesNotContain("Reunion de coordinacion ampliada");

        Integer siguenLasFilas = jdbc.sql("SELECT count(*) FROM manual_activity")
                .query(Integer.class).single();
        assertThat(siguenLasFilas)
                .as("retirar es active = false, no un DELETE: la fila no se pierde")
                .isEqualTo(antesDeRetirar);

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
    @DisplayName("paso 6: las tres formas de tipo se ofrecen y se guardan")
    void pasoSeisTresFormasDeTipo() {
        entrarComo("ana@ejemplo.test");
        pagina.navigate(url("/actividad-diaria"));
        pagina.locator("#description").waitFor();

        // El desplegable ofrece «Sin tipo» y los del catalogo; «Otro» es el campo libre.
        assertThat(pagina.locator("#typeId option").count())
                .as("«Sin tipo» mas los tipos del catalogo")
                .isGreaterThan(1);
        assertThat(pagina.locator("#typeId option[value='']").count()).isEqualTo(1);
        assertThat(pagina.locator("#otherType").count()).isEqualTo(1);

        // a) Sin tipo.
        pagina.fill("#description", "Atencion al publico");
        pagina.locator("button:has-text('Agregar actividad')").click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));
        assertThat(pagina.content()).contains("Atencion al publico");

        // b) Del catalogo.
        pagina.fill("#description", "Elaboracion de oficio");
        pagina.locator("#typeId").selectOption(new com.microsoft.playwright.options.SelectOption()
                .setLabel("Informe legal"));
        pagina.locator("button:has-text('Agregar actividad')").click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));
        assertThat(pagina.content()).contains("Elaboracion de oficio").contains("Informe legal");

        // c) Escrito a mano.
        pagina.fill("#description", "Coordinacion interna");
        pagina.fill("#otherType", "Reunion con Contabilidad");
        pagina.locator("button:has-text('Agregar actividad')").click();
        pagina.waitForURL(u -> u.contains("/actividad-diaria"));
        assertThat(pagina.content()).contains("Coordinacion interna")
                .contains("Reunion con Contabilidad");

        // Y el tipo escrito NO aparece en el catalogo.
        pagina.navigate(url("/tipos-de-pendiente"));
        assertThat(pagina.content())
                .as("el catalogo lo administra la jefa; una pantalla diaria no lo amplia")
                .doesNotContain("Reunion con Contabilidad");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 7: un tipo usado por una actividad manual no se puede borrar")
    void pasoSieteTipoProtegido() {
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/tipos-de-pendiente"));

        // El formulario de borrado pide confirmacion con un confirm() del navegador.
        pagina.onDialog(d -> d.accept());

        pagina.locator("tr:has-text('Informe legal') form[action$='/eliminar'] button").click();
        pagina.waitForURL(u -> u.contains("/tipos-de-pendiente"));

        String html = pagina.content();
        assertThat(html)
                .as("«en uso» sugiere deshabilitar; «en el historial» seria el mensaje "
                        + "equivocado, y una traza de integridad seria el fallo peor")
                .contains("en uso");
        assertThat(html).contains("Informe legal");

        Integer sigue = jdbc.sql("SELECT count(*) FROM pending_task_type WHERE id = :id")
                .param("id", tipoEnUso).query(Integer.class).single();
        assertThat(sigue).isEqualTo(1);

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 9: la jefa puede corregir lo de Ana, otro abogado no")
    void pasoNueveQuienPuedeCorregir() {
        // La jefa entra en la actividad de Ana y SI ve el formulario de correccion.
        entrarComo("jefa@ejemplo.test");
        pagina.navigate(url("/actividad-diaria?ownerId=" + abogadaA));
        assertThat(pagina.content())
                .as("RF-020 da la correccion al autor y a la jefa")
                .contains("Parte de trabajo de Ana").contains("/editar");

        // Beto la ve, porque la lectura es compartida, pero no puede tocarla.
        entrarComo("beto@ejemplo.test");
        pagina.navigate(url("/actividad-diaria?ownerId=" + abogadaA));
        String comoBeto = pagina.content();
        assertThat(comoBeto).as("la lectura es compartida").contains("Parte de trabajo de Ana");
        assertThat(comoBeto)
                .as("un abogado no toca el parte de trabajo de otro")
                .doesNotContain("/editar").doesNotContain("/retirar");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 10: una fecha futura la rechaza el servidor, no solo el navegador")
    void pasoDiezFechaFutura() {
        entrarComo("ana@ejemplo.test");
        pagina.navigate(url("/actividad-diaria"));
        pagina.locator("#performedOn").waitFor();

        // El campo lleva max=hoy, asi que el navegador ya lo impide. Se le quita para
        // comprobar la guarda del SERVIDOR, que es la que de verdad protege el dato:
        // un cliente que no respete el max no puede colar una fecha futura.
        assertThat(pagina.locator("#performedOn").getAttribute("max"))
                .as("el navegador tambien lo impide, que es lo comodo para el usuario")
                .isEqualTo(hoy.toString());
        pagina.evaluate("() => document.getElementById('performedOn').removeAttribute('max')");

        pagina.fill("#description", "Lo hare la semana que viene");
        pagina.fill("#performedOn", hoy.plusDays(7).toString());
        pagina.locator("button:has-text('Agregar actividad')").click();

        assertThat(pagina.content())
                .contains("No se puede registrar actividad de un día futuro");

        Integer escritas = jdbc.sql("""
                SELECT count(*) FROM manual_activity WHERE description = 'Lo hare la semana que viene'
                """).query(Integer.class).single();
        assertThat(escritas).as("una validacion fallida no deja rastro").isZero();

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 14: un pendiente con dos fechas sale en dos dias, no duplicado")
    void pasoCatorceDosFechas() {
        entrarComo("ana@ejemplo.test");

        // En la vista de dia se aisla cada fecha con su etiqueta.
        pagina.navigate(url("/calendario?vista=dia&ancla=" + hoy.plusDays(4)));
        String programadoEl = pagina.content();
        assertThat(programadoEl).contains("Informe programado y con plazo").contains("programado");

        pagina.navigate(url("/calendario?vista=dia&ancla=" + hoy.plusDays(6)));
        String venceEl = pagina.content();
        assertThat(venceEl).contains("Informe programado y con plazo").contains("vence");

        assertThat(erroresDeConsola).isEmpty();
    }

    @Test
    @DisplayName("paso 15: todo el equipo ve todo, y el calendario del area es explicito")
    void pasoQuinceLecturaCompartida() {
        entrarComo("beto@ejemplo.test");

        // Por omision, lo propio: Beto no tiene nada.
        pagina.navigate(url("/calendario?vista=mes"));
        assertThat(pagina.content()).doesNotContain("Audiencia de conciliacion");

        // Pedido explicitamente, el area entera.
        pagina.navigate(url("/calendario?vista=mes&todos=true"));
        assertThat(pagina.content())
                .as("el filtro por persona es comodidad, no permiso")
                .contains("Audiencia de conciliacion");

        // Y la actividad diaria de Ana se puede consultar, sin formulario de alta.
        pagina.navigate(url("/actividad-diaria?ownerId=" + abogadaA));
        assertThat(pagina.content()).contains("Parte de trabajo de Ana")
                .doesNotContain("Agregar actividad manual");

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
