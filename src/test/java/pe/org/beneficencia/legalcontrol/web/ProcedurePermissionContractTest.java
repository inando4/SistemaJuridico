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
 * Matriz de permisos de edicion sobre procedimientos administrativos.
 *
 * <p>Cada caso se prueba con una <b>peticion directa</b>: ocultar el boton de
 * editar no impide nada a quien componga la peticion a mano, asi que lo que se
 * comprueba es el rechazo del servidor y que el registro queda intacto.
 */
@AutoConfigureMockMvc
class ProcedurePermissionContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID abogadoA;
    private UUID procedimientoDeA;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        abogadoA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        procedimientoDeA = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, requesting_area,
                                                      active, created_at, updated_at, version)
                VALUES (:id, :owner, 'ADM-PERM-2026', 'Gerencia General', true, :ahora, :ahora, 1)
                """)
                .param("id", procedimientoDeA).param("owner", abogadoA)
                .param("ahora", ahora).update();
    }

    private void editarComo(String correo, String area, int estadoEsperado) throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, correo);
        mvc.perform(post("/administrativos/" + procedimientoDeA).session(sesion).with(csrf())
                        .param("fileNumber", "ADM-PERM-2026")
                        .param("requestingArea", area)
                        .param("version", "1"))
                .andExpect(status().is(estadoEsperado));
    }

    private String areaActual() {
        return jdbc.sql("SELECT requesting_area FROM administrative_procedure WHERE id = :id")
                .param("id", procedimientoDeA).query(String.class).single();
    }

    @Test
    @DisplayName("el responsable edita lo suyo")
    void propietarioEdita() throws Exception {
        editarComo("a@ejemplo.test", "Administracion", 302);
        assertThat(areaActual()).isEqualTo("Administracion");
    }

    @Test
    @DisplayName("un abogado NO puede editar el procedimiento de otro, ni por peticion directa")
    void ajenoRechazado() throws Exception {
        editarComo("b@ejemplo.test", "Intento indebido", 403);
        assertThat(areaActual()).as("el registro debe quedar intacto").isEqualTo("Gerencia General");
    }

    @Test
    @DisplayName("la jefatura edita cualquiera y queda auditado como intervencion")
    void jefaEditaAjeno() throws Exception {
        editarComo("jefa@ejemplo.test", "Corregido por jefatura", 302);

        var fila = jdbc.sql("""
                SELECT actor_id, owner_id FROM audit_event
                WHERE entity_id = :id AND action = 'UPDATE'
                """).param("id", procedimientoDeA).query().singleRow();

        assertThat(fila.get("owner_id")).as("el responsable registrado es el abogado A")
                .isEqualTo(abogadoA);
        assertThat(fila.get("actor_id")).as("el autor es la jefatura").isNotEqualTo(abogadoA);
    }

    @Test
    @DisplayName("el formulario de edicion ajeno tampoco se abre")
    void formularioAjenoRechazado() throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, "b@ejemplo.test");
        mvc.perform(get("/administrativos/" + procedimientoDeA + "/editar").session(sesion))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("consultar uno ajeno si esta permitido, y no genera historial")
    void lecturaCompartidaSinHistorial() throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, "b@ejemplo.test");
        mvc.perform(get("/administrativos/" + procedimientoDeA).session(sesion))
                .andExpect(status().isOk());
        mvc.perform(get("/administrativos/" + procedimientoDeA + "/historial").session(sesion))
                .andExpect(status().isOk());

        Integer eventos = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(eventos).as("leer no escribe historial").isZero();
    }

    @Test
    @DisplayName("cambiar la visibilidad de uno ajeno se rechaza")
    void visibilidadAjenaRechazada() throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, "b@ejemplo.test");
        mvc.perform(post("/administrativos/" + procedimientoDeA + "/visibilidad")
                        .session(sesion).with(csrf())
                        .param("active", "false").param("version", "1"))
                .andExpect(status().isForbidden());
    }
}
