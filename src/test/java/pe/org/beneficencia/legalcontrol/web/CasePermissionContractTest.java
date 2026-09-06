package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
 * Matriz de permisos de edicion (FR-010).
 *
 * <p>Cada caso se prueba con una <b>peticion directa</b>, no pulsando botones:
 * ocultar el boton de editar no impide nada a quien componga la peticion a mano,
 * asi que lo que se comprueba es el rechazo del servidor.
 */
@AutoConfigureMockMvc
class CasePermissionContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID abogadoA;
    private UUID abogadoB;
    private UUID expedienteDeA;
    private long versionDeA;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        abogadoA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        expedienteDeA = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-PERM-2026', 'Desalojo', true, :ahora, :ahora, 1)
                """)
                .param("id", expedienteDeA).param("owner", abogadoA).param("ahora", ahora).update();
        versionDeA = 1;
    }

    private void editarComo(String correo, String materia, int estadoEsperado) throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, correo);
        mvc.perform(post("/judicial-cases/" + expedienteDeA).session(sesion).with(csrf())
                        .param("caseNumber", "EXP-PERM-2026")
                        .param("subject", materia)
                        .param("version", String.valueOf(versionDeA)))
                .andExpect(status().is(estadoEsperado));
    }

    @Test
    @DisplayName("el responsable edita lo suyo")
    void propietarioEdita() throws Exception {
        editarComo("a@ejemplo.test", "Desalojo y pago", 302);

        String materia = jdbc.sql("SELECT subject FROM judicial_case WHERE id = :id")
                .param("id", expedienteDeA).query(String.class).single();
        assertThat(materia).isEqualTo("Desalojo y pago");
    }

    @Test
    @DisplayName("un abogado NO puede editar el expediente de otro, ni por peticion directa")
    void ajenoRechazado() throws Exception {
        editarComo("b@ejemplo.test", "Intento indebido", 403);

        String materia = jdbc.sql("SELECT subject FROM judicial_case WHERE id = :id")
                .param("id", expedienteDeA).query(String.class).single();
        assertThat(materia).as("el registro debe quedar intacto").isEqualTo("Desalojo");
    }

    @Test
    @DisplayName("la jefa edita cualquier expediente y queda auditado como intervencion")
    void jefaEditaAjeno() throws Exception {
        editarComo("jefa@ejemplo.test", "Corregido por jefatura", 302);

        var fila = jdbc.sql("""
                SELECT actor_id, owner_id FROM audit_event
                WHERE entity_id = :id AND action = 'UPDATE'
                """).param("id", expedienteDeA).query().singleRow();

        assertThat(fila.get("owner_id")).as("el responsable registrado debe ser el abogado A")
                .isEqualTo(abogadoA);
        assertThat(fila.get("actor_id")).as("el autor debe ser la jefa")
                .isNotEqualTo(abogadoA);
    }

    @Test
    @DisplayName("el formulario de edicion ajeno tampoco se abre")
    void formularioAjenoRechazado() throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, "b@ejemplo.test");
        mvc.perform(get("/judicial-cases/" + expedienteDeA + "/edit").session(sesion))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("consultar un expediente ajeno si esta permitido, y no genera historial")
    void lecturaCompartidaSinHistorial() throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, "b@ejemplo.test");
        mvc.perform(get("/judicial-cases/" + expedienteDeA).session(sesion))
                .andExpect(status().isOk());
        mvc.perform(get("/judicial-cases/" + expedienteDeA + "/history").session(sesion))
                .andExpect(status().isOk());

        Integer eventos = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(eventos).as("leer no escribe historial").isZero();
    }

    @Test
    @DisplayName("cambiar la visibilidad de un expediente ajeno se rechaza")
    void visibilidadAjenaRechazada() throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, "b@ejemplo.test");
        mvc.perform(post("/judicial-cases/" + expedienteDeA + "/visibility")
                        .session(sesion).with(csrf())
                        .param("active", "false").param("version", "1"))
                .andExpect(status().isForbidden());
    }
}
