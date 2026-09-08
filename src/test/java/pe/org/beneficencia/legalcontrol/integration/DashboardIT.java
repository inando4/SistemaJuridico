package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.dashboard.DashboardRepository;

/**
 * Las seis tarjetas del dashboard (insumo, seccion 23).
 *
 * <p>Se comprueba <b>la cuenta</b> contra el repositorio y <b>la coincidencia</b>
 * entre lo que cuenta cada tarjeta y lo que muestra el listado al que enlaza. Lo
 * segundo es lo que mas fallos destapa: dos condiciones escritas por separado
 * divergen en cuanto una usa un limite inclusivo y la otra no.
 */
@AutoConfigureMockMvc
class DashboardIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private DashboardRepository resumenes;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID otro;
    private UUID tipo;
    private UUID prioridad;
    private UUID estado;
    private LocalDate hoy;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        otro = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");

        hoy = LocalDate.now();
        DatosSinteticos.sembrarCalendario(jdbc, yo, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);

        Timestamp ahora = Timestamp.from(Instant.now());
        tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "Informe", 1, yo, ahora)
                .get(0);
        prioridad = DatosSinteticos.sembrarCatalogo(jdbc, "priority", "Alta", 1, yo, ahora).get(0);
        estado = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "Pendiente", 1, yo,
                ahora).get(0);

        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    /** Crea un pendiente. Las fechas nulas se omiten. */
    private UUID crear(UUID responsable, String titulo, LocalDate limite,
                       LocalDate programado, LocalDate recepcion, Instant cumplido) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task
                    (id, owner_id, title, pending_task_type_id, priority_id,
                     pending_task_status_id, received_at, registered_at, scheduled_for,
                     deadline, completed_at, active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :tipo, :prioridad, :estado,
                        :recepcion, :recepcion, :programado, :limite, :cumplido,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", responsable).param("titulo", titulo)
                .param("tipo", tipo).param("prioridad", prioridad).param("estado", estado)
                .param("recepcion", recepcion == null ? hoy : recepcion)
                .param("programado", programado).param("limite", limite)
                .param("cumplido", cumplido == null ? null : Timestamp.from(cumplido))
                .param("ahora", ahora)
                .update();
        return id;
    }

    private DashboardRepository.Cuentas cuentasDe(UUID responsable, LocalDate frontera3,
                                                  LocalDate hace15) {
        return resumenes.contar(responsable, hoy, frontera3, hace15, hoy.withDayOfMonth(1));
    }

    /** Fronteras generosas: solo importa que existan, no su valor exacto. */
    private DashboardRepository.Cuentas misCuentas() {
        return cuentasDe(yo, hoy.plusDays(5), hoy.minusDays(30));
    }

    // ------------------------------------------------------------------ T009

    @Test
    @DisplayName("cada tarjeta cuenta los pendientes de su situacion")
    void seisSituaciones() {
        crear(yo, "Vencido", hoy.minusDays(3), null, null, null);
        crear(yo, "Vence hoy", hoy, null, null, null);
        crear(yo, "Programado hoy", null, hoy, null, null);
        crear(yo, "Vence pronto", hoy.plusDays(2), null, null, null);
        crear(yo, "Sin plazo antiguo", null, null, hoy.minusDays(60), null);
        crear(yo, "Cumplido", null, null, null, Instant.now());

        var c = misCuentas();

        assertThat(c.vencidos()).as("solo el de plazo anterior a hoy").isEqualTo(1);
        assertThat(c.urgentesHoy())
                .as("la tarjeta agrupa «vence hoy» y «programado para hoy» (seccion 23)")
                .isEqualTo(2);
        assertThat(c.proximos()).as("el que vence dentro de la ventana").isEqualTo(1);
        assertThat(c.sinPlazo()).as("el que lleva dos meses sin plazo").isEqualTo(1);
        assertThat(c.activos()).as("los cinco sin cumplir").isEqualTo(5);
        assertThat(c.cumplidosMes()).isEqualTo(1);
    }

    @Test
    @DisplayName("los que vencen hoy no se cuentan como proximos")
    void hoyNoEsProximo() {
        crear(yo, "Vence hoy", hoy, null, null, null);

        assertThat(misCuentas().proximos())
                .as("«proximos» empieza manana; si no, se contaria dos veces lo de hoy")
                .isZero();
    }

    // ------------------------------------------------------------------ T010

    @Test
    @DisplayName("un pendiente de otro responsable no entra en ninguna cuenta")
    void loAjenoNoCuenta() {
        crear(otro, "Vencido ajeno", hoy.minusDays(3), null, null, null);
        crear(otro, "Cumplido ajeno", null, null, null, Instant.now());
        crear(otro, "Sin plazo ajeno", null, null, hoy.minusDays(60), null);

        var c = misCuentas();

        assertThat(c.vencidos()).isZero();
        assertThat(c.activos()).isZero();
        assertThat(c.cumplidosMes()).isZero();
        assertThat(c.sinPlazo()).isZero();
    }

    // ------------------------------------------------------------------ T011

    @Test
    @DisplayName("al cumplir sale de las tarjetas de urgencia y entra en las del mes")
    void cumplirCambiaDeTarjeta() {
        UUID id = crear(yo, "Se cumplira", hoy.minusDays(3), null, null, null);
        assertThat(misCuentas().vencidos()).isEqualTo(1);

        jdbc.sql("UPDATE pending_task SET completed_at = now() WHERE id = :id")
                .param("id", id).update();

        var c = misCuentas();
        assertThat(c.vencidos())
                .as("estar cumplido pesa mas que la fecha: ya no es urgente")
                .isZero();
        assertThat(c.activos()).isZero();
        assertThat(c.cumplidosMes()).isEqualTo(1);
    }

    @Test
    @DisplayName("un cumplido del mes pasado no cuenta en «este mes»")
    void cumplidoDelMesPasado() {
        UUID id = crear(yo, "De otro mes", null, null, null, Instant.now());
        // Al dia anterior al primero del mes en curso.
        jdbc.sql("UPDATE pending_task SET completed_at = :cuando WHERE id = :id")
                .param("cuando", Timestamp.valueOf(
                        hoy.withDayOfMonth(1).minusDays(1).atStartOfDay()))
                .param("id", id).update();

        assertThat(misCuentas().cumplidosMes())
                .as("el corte es el mes calendario, no los ultimos treinta dias")
                .isZero();
    }

    // ------------------------------------------------------------------ T012

    @Test
    @DisplayName("sin calendario, las dos cifras que dependen de el no salen; las otras si")
    void sinCalendarioSoloDosSeCallan() throws Exception {
        crear(yo, "Vencido", hoy.minusDays(3), null, null, null);
        crear(yo, "Vence hoy", hoy, null, null, null);
        crear(yo, "Sin plazo antiguo", null, null, hoy.minusDays(60), null);

        // Se retira la confirmacion de cobertura de todos los anos.
        jdbc.sql("DELETE FROM calendar_review").update();

        String html = mvc.perform(get("/").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("un numero inventado es peor que ninguno: parece fiable")
                .contains("Sin dato");
        assertThat(html).contains("Faltan días no laborables por revisar");

        // Vencidos y urgentes solo comparan fechas: no dependen del calendario.
        var c = cuentasDe(yo, null, null);
        assertThat(c.vencidos()).isEqualTo(1);
        assertThat(c.urgentesHoy()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ T020

    @Test
    @DisplayName("el listado de cada tarjeta contiene exactamente lo que la tarjeta contaba")
    void tarjetaYListadoCoinciden() throws Exception {
        crear(yo, "Vencido uno", hoy.minusDays(3), null, null, null);
        crear(yo, "Vencido dos", hoy.minusDays(1), null, null, null);
        crear(yo, "Vence hoy", hoy, null, null, null);
        crear(yo, "Programado hoy", null, hoy, null, null);
        crear(yo, "Vence pronto", hoy.plusDays(1), null, null, null);
        crear(yo, "Sin plazo antiguo", null, null, hoy.minusDays(60), null);
        crear(yo, "Sin plazo reciente", null, null, hoy.minusDays(2), null);
        crear(yo, "Cumplido del mes", null, null, null, Instant.now());
        crear(otro, "Ajeno vencido", hoy.minusDays(3), null, null, null);

        String panel = mvc.perform(get("/").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertThat(panel).as("la pantalla debe abrir").contains("Vencidos");

        // Para cada foco: exactamente estos titulos, ni uno mas.
        comprobarFoco(panel, "vencidos", List.of("Vencido uno", "Vencido dos"));
        comprobarFoco(panel, "hoy", List.of("Vence hoy", "Programado hoy"));
        comprobarFoco(panel, "proximos", List.of("Vence pronto"));
        comprobarFoco(panel, "sin-plazo-antiguos", List.of("Sin plazo antiguo"));
        comprobarFoco(panel, "activos", List.of("Vencido uno", "Vencido dos", "Vence hoy",
                "Programado hoy", "Vence pronto", "Sin plazo antiguo", "Sin plazo reciente"));
        comprobarFoco(panel, "cumplidos-del-mes", List.of("Cumplido del mes"));
    }

    /** Todos los titulos de la prueba, para saber cuales NO deben salir. */
    private static final List<String> TODOS = List.of(
            "Vencido uno", "Vencido dos", "Vence hoy", "Programado hoy", "Vence pronto",
            "Sin plazo antiguo", "Sin plazo reciente", "Cumplido del mes", "Ajeno vencido");

    /**
     * Comprueba que el listado con ese foco trae exactamente esos pendientes.
     *
     * <p>Se comprueban los titulos presentes <b>y los ausentes</b>: contar filas
     * dejaria pasar un listado con los mismos numeros pero otros pendientes.
     */
    private void comprobarFoco(String panel, String foco, List<String> esperados)
            throws Exception {
        // Se sigue el enlace que pinta la tarjeta, no una URL inventada aqui: lo
        // que SC-004 exige es que «la tarjeta lleve» a esos pendientes, y una URL
        // escrita a mano en la prueba podria no ser la que la pantalla usa.
        var enlace = java.util.regex.Pattern
                .compile("href=\"([^\"]*alerta=" + foco + "[^\"]*)\"")
                .matcher(panel);
        assertThat(enlace.find()).as("la tarjeta «%s» debe enlazar a su listado", foco).isTrue();
        String url = enlace.group(1).replace("&amp;", "&");

        String html = mvc.perform(get(url).session(sesion))
                .andReturn().getResponse().getContentAsString();

        for (String titulo : TODOS) {
            boolean presente = html.contains(">" + titulo + "<");
            assertThat(presente)
                    .as("foco «%s»: «%s» %s deberia estar", foco, titulo,
                            esperados.contains(titulo) ? "si" : "no")
                    .isEqualTo(esperados.contains(titulo));
        }
    }

    @Test
    @DisplayName("un foco que no existe se rechaza, no se ignora")
    void focoInvalido() throws Exception {
        mvc.perform(get("/pendientes?alerta=loQueSea").session(sesion))
                .andExpect(status().isUnprocessableEntity());
    }
}
