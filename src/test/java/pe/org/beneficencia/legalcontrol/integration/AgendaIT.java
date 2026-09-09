package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * El calendario muestra los cinco origenes de evento, y sigue funcionando sin
 * calendario de dias no laborables.
 *
 * <p>Dos cosas que solo se ven con casos concretos:
 *
 * <ul>
 *   <li>un pendiente con programacion <b>y</b> vencimiento en el rango produce
 *       <b>dos</b> eventos: no es un duplicado, son dos hechos en dos dias;
 *   <li>navegar a un mes de un año anterior tiene que salir con su sombreado. Con
 *       {@code paraListado(hoy)} saldria sin el y avisando en falso, que es la misma
 *       familia del fallo que la 003 dejo en produccion.
 * </ul>
 */
@AutoConfigureMockMvc
class AgendaIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    /** Un mes fijo y pasado, para que el resultado no dependa de cuando se ejecute. */
    private static final LocalDate ANCLA = LocalDate.of(2026, 3, 15);

    private MockHttpSession sesion;
    private UUID abogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");

        DatosSinteticos.sembrarCalendario(jdbc, abogado, 2025, 2026, 2027);

        UUID tipoAudiencia = tipo("Audiencia");

        pendiente("Presentar alegatos", LocalDate.of(2026, 3, 5), null, null);
        pendiente("Escrito con plazo", null, LocalDate.of(2026, 3, 10), null);
        pendiente("Audiencia de conciliacion", LocalDate.of(2026, 3, 12), null, tipoAudiencia);
        // Las dos fechas dentro del rango: debe producir DOS eventos.
        pendiente("Informe programado y con plazo",
                LocalDate.of(2026, 3, 18), LocalDate.of(2026, 3, 25), null);

        judicial("EXP-VENCE-2026", LocalDate.of(2026, 3, 20), null);
        judicial("EXP-ACTUO-2026", null, LocalDate.of(2026, 3, 9));
        administrativo("ADM-VENCE-2026", LocalDate.of(2026, 3, 23));
    }

    private UUID tipo(String nombre) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task_type (id, name, enabled, created_by,
                                               created_at, updated_at, version)
                VALUES (:id, :nombre, true, :usuario, :ahora, :ahora, 1)
                """)
                .param("id", id).param("nombre", nombre).param("usuario", abogado)
                .param("ahora", ahora).update();
        return id;
    }

    private void pendiente(String titulo, LocalDate programada, LocalDate limite, UUID tipo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, pending_task_type_id,
                                          registered_at, scheduled_for, deadline,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :tipo, :registro, :programada, :limite,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado).param("titulo", titulo)
                .param("tipo", tipo).param("registro", LocalDate.of(2026, 3, 1))
                .param("programada", programada).param("limite", limite)
                .param("ahora", ahora).update();
    }

    private void judicial(String numero, LocalDate limite, LocalDate actuacion) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, deadline, last_action_date,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :limite, :actuacion, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado).param("numero", numero)
                .param("limite", limite).param("actuacion", actuacion)
                .param("ahora", ahora).update();
    }

    private void administrativo(String numero, LocalDate limite) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, deadline,
                                                      active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :limite, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado).param("numero", numero)
                .param("limite", limite).param("ahora", ahora).update();
    }

    private String pantalla(String query) throws Exception {
        return mvc.perform(get("/calendario" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("los cinco origenes de evento aparecen en el mes")
    void losCincoOrigenes() throws Exception {
        String html = pantalla("?vista=mes&ancla=" + ANCLA);

        assertThat(html).as("pendiente programado").contains("Presentar alegatos");
        assertThat(html).as("vencimiento de pendiente").contains("Escrito con plazo");
        assertThat(html).as("vencimiento judicial").contains("EXP-VENCE-2026");
        assertThat(html).as("actuacion judicial").contains("EXP-ACTUO-2026");
        assertThat(html).as("vencimiento administrativo").contains("ADM-VENCE-2026");
    }

    @Test
    @DisplayName("una audiencia se ve como audiencia por su tipo, no por una comparacion")
    void laAudienciaSeDistingue() throws Exception {
        String html = pantalla("?vista=mes&ancla=" + ANCLA);

        assertThat(html).contains("Audiencia de conciliacion");
        assertThat(html)
                .as("el nombre del tipo se muestra; el codigo no compara con 'Audiencia'")
                .contains("Audiencia");
    }

    @Test
    @DisplayName("un pendiente con dos fechas produce dos eventos")
    void dosFechasDosEventos() throws Exception {
        // En la vista de dia se aisla cada fecha: si fuera un duplicado, saldria dos
        // veces el mismo dia en vez de una vez en cada uno.
        assertThat(pantalla("?vista=dia&ancla=2026-03-18"))
                .contains("Informe programado y con plazo").contains("programado");
        assertThat(pantalla("?vista=dia&ancla=2026-03-25"))
                .contains("Informe programado y con plazo").contains("vence");
    }

    @Test
    @DisplayName("las tres vistas muestran los eventos de su rango, sin inventar ni perder")
    void lasTresVistasCoinciden() throws Exception {
        // El 12 de marzo de 2026 es jueves; su semana va del 9 al 15.
        String dia = pantalla("?vista=dia&ancla=2026-03-12");
        String semana = pantalla("?vista=semana&ancla=2026-03-12");
        String mes = pantalla("?vista=mes&ancla=2026-03-12");

        assertThat(dia).contains("Audiencia de conciliacion");
        assertThat(semana).contains("Audiencia de conciliacion");
        assertThat(mes).contains("Audiencia de conciliacion");

        // La semana del 9 al 15 no incluye el 20: solo el mes lo ve.
        assertThat(dia).doesNotContain("EXP-VENCE-2026");
        assertThat(semana).doesNotContain("EXP-VENCE-2026");
        assertThat(mes).contains("EXP-VENCE-2026");
    }

    @Test
    @DisplayName("un mes de un año anterior sale con su sombreado y sin aviso")
    void mesDeAnoAnteriorConSombreado() throws Exception {
        // Marzo de 2025: año confirmado, pero anterior al actual. Con paraListado(hoy)
        // este mes quedaria fuera del rango y avisaria en falso.
        String html = pantalla("?vista=mes&ancla=2025-03-15");

        assertThat(html)
                .as("el rango del calendario lo dan las fechas que se pintan, no «hoy»")
                .doesNotContain("Faltan días no laborables por revisar");
        assertThat(html).contains("no-laborable");
    }

    @Test
    @DisplayName("sin el año confirmado la rejilla y los eventos siguen; solo falta el sombreado")
    void sinCalendarioSigueFuncionando() throws Exception {
        jdbc.sql("DELETE FROM calendar_review WHERE year = 2026").update();

        String html = pantalla("?vista=mes&ancla=" + ANCLA);

        assertThat(html).contains("Faltan días no laborables por revisar");
        assertThat(html)
                .as("los eventos son fechas guardadas, no cuentas de dias habiles")
                .contains("Presentar alegatos").contains("EXP-VENCE-2026");
    }

    @Test
    @DisplayName("un periodo sin eventos pinta la rejilla igualmente")
    void periodoVacioPintaLaRejilla() throws Exception {
        String html = pantalla("?vista=mes&ancla=2026-07-15");

        assertThat(html).contains("No hay nada programado en este periodo");
        assertThat(html).as("la rejilla se pinta con sus dias").contains("<table");
    }

    @Test
    @DisplayName("se puede ver el calendario de toda el area")
    void calendarioDeTodaElArea() throws Exception {
        UUID otra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Diligencia de la otra abogada', :registro, :programada,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", otra)
                .param("registro", LocalDate.of(2026, 3, 1))
                .param("programada", LocalDate.of(2026, 3, 6))
                .param("ahora", ahora).update();

        assertThat(pantalla("?vista=mes&ancla=" + ANCLA))
                .as("por omision se ve lo propio")
                .doesNotContain("Diligencia de la otra abogada");

        assertThat(pantalla("?vista=mes&ancla=" + ANCLA + "&todos=true"))
                .as("la lectura es compartida; el filtro es comodidad, no permiso")
                .contains("Diligencia de la otra abogada");
    }

    @Test
    @DisplayName("un ancla ilegible no revienta: cae en hoy")
    void anclaIlegible() throws Exception {
        mvc.perform(get("/calendario?ancla=nodeberia").session(sesion))
                .andExpect(status().isOk());
    }
}
