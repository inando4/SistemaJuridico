package pe.org.beneficencia.legalcontrol.web;

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

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * El bloque «Pendientes relacionados» de las secciones 28 y 30 del insumo.
 *
 * <p>Los datos son sinteticos: nombres inventados y numeros de expediente que no
 * corresponden a ningun caso real.
 */
@AutoConfigureMockMvc
class ExpedientePendientesContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID laOtra;
    private UUID expediente;
    private UUID expedienteVacio;
    private UUID procedimiento;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        laOtra = SesionDePrueba.crearCuenta(jdbc, encoder, "colega@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Colega Inventada' WHERE id = :id")
                .param("id", laOtra).update();
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        expediente = judicial("EXP-FICHA-2026");
        expedienteVacio = judicial("EXP-SIN-NADA-2026");
        procedimiento = administrativo("ADM-FICHA-2026");

        pendiente(yo, "Redactar contestacion", expediente, null, true, false);
        pendiente(laOtra, "Revisar antecedentes", expediente, null, true, false);
        pendiente(yo, "Escrito ya presentado", expediente, null, true, true);
        pendiente(yo, "Diligencia retirada", expediente, null, false, false);
        pendiente(yo, "Informe del procedimiento", null, procedimiento, true, false);
    }

    private UUID judicial(String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", yo).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private UUID administrativo(String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", yo).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private void pendiente(UUID owner, String titulo, UUID j, UUID a,
                           boolean visible, boolean cumplido) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                          administrative_procedure_id, registered_at,
                                          deadline, completed_at, active,
                                          created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :a, :hoy, CAST(:lim AS date), :fin, :vis, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("j", j).param("a", a).param("hoy", LocalDate.now())
                .param("lim", LocalDate.now().plusDays(10).toString())
                .param("fin", cumplido ? ahora : null)
                .param("vis", visible).param("ts", ahora).update();
    }

    private String ficha(String ruta) throws Exception {
        return mvc.perform(get(ruta).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("la ficha judicial muestra el bloque con los datos de cada fila (RF-003)")
    void bloqueEnLaFichaJudicial() throws Exception {
        String html = ficha("/judiciales/" + expediente);

        assertThat(html)
                .contains("Pendientes relacionados")
                .contains("Redactar contestacion")
                .as("cada titulo enlaza a su ficha")
                .contains("/pendientes/");
    }

    @Test
    @DisplayName("aparecen los pendientes de otro responsable, con su nombre (RF-004)")
    void losDeOtroTambien() throws Exception {
        // Una implementacion que filtrara por owner_id = el de la sesion pasaria
        // todas las demas pruebas con datos de un solo usuario. Por eso el fixture
        // tiene dos cuentas y esta prueba exige ver la ajena.
        String html = ficha("/judiciales/" + expediente);

        assertThat(html)
                .as("la lectura es compartida: el bloque es del expediente, no de quien mira")
                .contains("Revisar antecedentes")
                .as("y se dice de quien es, para que nadie lo confunda con trabajo propio")
                .contains("Colega Inventada");
    }

    @Test
    @DisplayName("los cumplidos se ven y los archivados no (RF-002, RF-007)")
    void cumplidosSiArchivadosNo() throws Exception {
        String html = ficha("/judiciales/" + expediente);

        assertThat(html)
                .as("lo cumplido es historia del expediente y se queda")
                .contains("Escrito ya presentado")
                .as("lo retirado se quito a proposito")
                .doesNotContain("Diligencia retirada");
    }

    @Test
    @DisplayName("sin pendientes, el bloque sigue estando con mensaje de vacio (RF-005)")
    void bloqueVacio() throws Exception {
        String html = ficha("/judiciales/" + expedienteVacio);

        assertThat(html)
                .as("el bloque no desaparece: un hueco en blanco no distingue «no hay» de «no cargo»")
                .contains("Pendientes relacionados")
                .contains("no tiene ningún pendiente registrado")
                .doesNotContain("Redactar contestacion");
    }

    @Test
    @DisplayName("la ficha administrativa lleva el mismo bloque (seccion 30)")
    void bloqueEnLaFichaAdministrativa() throws Exception {
        String html = ficha("/administrativos/" + procedimiento);

        assertThat(html)
                .contains("Pendientes relacionados")
                .contains("Informe del procedimiento")
                .as("es el de este procedimiento, no el de cualquier expediente")
                .doesNotContain("Redactar contestacion");
    }

    @Test
    @DisplayName("el bloque ofrece crear un pendiente relacionado y ver el listado filtrado")
    void enlacesDelBloque() throws Exception {
        String html = ficha("/judiciales/" + expediente);

        assertThat(html)
                .contains("+ Crear nuevo pendiente relacionado")
                .contains("judicialCaseId=" + expediente);
    }

    @Test
    @DisplayName("un expediente archivado sigue abriendose con su bloque")
    void expedienteArchivado() throws Exception {
        jdbc.sql("UPDATE judicial_case SET active = false WHERE id = :id")
                .param("id", expediente).update();

        assertThat(ficha("/judiciales/" + expediente))
                .contains("Pendientes relacionados")
                .contains("Redactar contestacion");
    }

    @Test
    @DisplayName("un expediente inexistente sigue devolviendo 404")
    void expedienteInexistente() throws Exception {
        mvc.perform(get("/judiciales/" + UUID.randomUUID()).session(sesion))
                .andExpect(status().isNotFound());
    }
}
