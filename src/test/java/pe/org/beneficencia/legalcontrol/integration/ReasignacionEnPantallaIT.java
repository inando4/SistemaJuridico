package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * Lo que la reasignacion deja <b>visible</b>.
 *
 * <p>Que la evidencia se guarde no basta: la 003 escribio las reprogramaciones en
 * el historial y nunca las pinto, y el fallo sobrevivio hasta produccion porque
 * ninguna prueba miraba la pantalla. Estas si.
 */
@AutoConfigureMockMvc
class ReasignacionEnPantallaIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private UUID jefaId;
    private UUID abogadaA;
    private UUID abogadoB;
    private UUID abogadaC;
    private List<UUID> ids;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        abogadaC = SesionDePrueba.crearCuenta(jdbc, encoder, "c@ejemplo.test", "LAWYER");
        ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 2, 1);
    }

    private void reasignar() {
        long version = jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", ids.get(0)).query(Long.class).single();
        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB, version,
                ReasignacionIT.cuenta(jefaId, "HEAD"));
    }

    private String pagina(String ruta, String correo) throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, correo);
        return mvc.perform(get(ruta).session(sesion)).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("el historial del expediente muestra los dos nombres, no identificadores")
    void historialDelExpedienteConNombres() throws Exception {
        reasignar();

        String html = pagina("/judiciales/" + ids.get(0) + "/historial", "jefa@ejemplo.test");

        assertThat(html).contains("Cambio de responsable");
        assertThat(html)
                .as("un identificador en pantalla no le dice nada a nadie")
                .doesNotContain(abogadoB.toString());
        assertThat(html).contains("Usuario a@ejemplo.test", "Usuario b@ejemplo.test");
    }

    @Test
    @DisplayName("el historial de cada pendiente movido tambien lo muestra")
    void historialDelPendiente() throws Exception {
        reasignar();

        String html = pagina("/pendientes/" + ids.get(1) + "/historial", "jefa@ejemplo.test");

        assertThat(html).contains("Cambio de responsable");
    }

    @Test
    @DisplayName("la jefa ve el formulario de reasignacion en la ficha; un abogado no")
    void soloLaJefaVeElFormulario() throws Exception {
        assertThat(pagina("/judiciales/" + ids.get(0), "jefa@ejemplo.test"))
                .contains("Cambiar de responsable", "Nuevo responsable");

        assertThat(pagina("/judiciales/" + ids.get(0), "a@ejemplo.test"))
                .as("ocultarlo no es la autorizacion, pero tampoco se ofrece lo que no se puede")
                .doesNotContain("Cambiar de responsable");
    }

    @Test
    @DisplayName("el aviso previo nombra al tercero que perdera el acceso")
    void elAvisoNombraAlTercero() throws Exception {
        DatosSinteticos.pendienteDeExpediente(jdbc, abogadaC, ids.get(0), "Informe de C", false);

        String html = pagina("/judiciales/" + ids.get(0), "jefa@ejemplo.test");

        assertThat(html).contains("Se traspasarán");
        assertThat(html)
                .as("quitarle trabajo a alguien no puede pasar en silencio")
                .contains("Usuario c@ejemplo.test")
                .contains("acceso de edición");
    }

    @Test
    @DisplayName("un expediente sin pendientes lo dice en el aviso")
    void avisoDeExpedienteVacio() throws Exception {
        List<UUID> vacio = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 0, 0);

        assertThat(pagina("/judiciales/" + vacio.get(0), "jefa@ejemplo.test"))
                .contains("no tiene pendientes");
    }
}
