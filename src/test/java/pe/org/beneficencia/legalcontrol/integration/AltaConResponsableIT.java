package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * Elegir responsable al dar de alta (insumo, seccion 5.3).
 *
 * <p>La prueba que importa es la del abogado que <b>envia {@code ownerId} de todas
 * formas</b>: la autorizacion no puede depender de que el formulario muestre o no
 * el campo. Quien mande el parametro a mano tiene que quedar como responsable el
 * mismo, no la persona que puso en el campo.
 */
@AutoConfigureMockMvc
class AltaConResponsableIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID jefaId;
    private UUID abogadaA;
    private UUID abogadoB;
    private UUID estado;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        estado = DatosSinteticos.sembrarCatalogo(jdbc, "procedural_status", "En trámite", 1,
                jefaId, java.sql.Timestamp.from(java.time.Instant.now())).get(0);
    }

    private UUID darDeAlta(String correo, UUID responsablePedido, String numero)
            throws Exception {
        MockHttpSession sesion = SesionDePrueba.entrar(mvc, correo);
        var peticion = post("/judiciales").session(sesion).with(csrf())
                .param("caseNumber", numero)
                .param("claimant", "Parte")
                .param("respondent", "Contraparte")
                .param("subject", "Materia")
                .param("proceduralStatusId", estado.toString());
        if (responsablePedido != null) {
            peticion = peticion.param("ownerId", responsablePedido.toString());
        }
        mvc.perform(peticion);

        return jdbc.sql("SELECT owner_id FROM judicial_case WHERE case_number = :n")
                .param("n", numero).query(UUID.class).single();
    }

    @Test
    @DisplayName("la jefa da de alta a nombre de otra persona")
    void laJefaEligeResponsable() throws Exception {
        assertThat(darDeAlta("jefa@ejemplo.test", abogadoB, "EXP-JEFA-1"))
                .isEqualTo(abogadoB);
    }

    @Test
    @DisplayName("un abogado queda como responsable aunque envie otro ownerId")
    void elAbogadoNoPuedeAsignarACualquiera() throws Exception {
        assertThat(darDeAlta("a@ejemplo.test", abogadoB, "EXP-ABOG-1"))
                .as("la autorizacion no depende de lo que el formulario muestre")
                .isEqualTo(abogadaA);
    }

    @Test
    @DisplayName("sin elegir responsable, queda a nombre de quien registra")
    void sinElegirQuedaAMiNombre() throws Exception {
        assertThat(darDeAlta("jefa@ejemplo.test", null, "EXP-JEFA-2")).isEqualTo(jefaId);
    }

    @Test
    @DisplayName("el desplegable de responsable solo se ofrece a la jefatura")
    void elCampoSoloParaLaJefatura() throws Exception {
        MockHttpSession deLaJefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
        assertThat(mvc.perform(get("/judiciales/nuevo").session(deLaJefa))
                .andReturn().getResponse().getContentAsString())
                .contains("Responsable");

        MockHttpSession deAbogada = SesionDePrueba.entrar(mvc, "a@ejemplo.test");
        assertThat(mvc.perform(get("/judiciales/nuevo").session(deAbogada))
                .andReturn().getResponse().getContentAsString())
                .doesNotContain("name=\"ownerId\"");
    }

    @Test
    @DisplayName("el historial del alta NO tiene responsable anterior")
    void elAltaNoTieneResponsableAnterior() throws Exception {
        darDeAlta("jefa@ejemplo.test", abogadoB, "EXP-HIST-1");
        UUID id = jdbc.sql("SELECT id FROM judicial_case WHERE case_number = 'EXP-HIST-1'")
                .query(UUID.class).single();

        var fila = jdbc.sql("""
                SELECT actor_id, owner_id, before_values FROM audit_event
                WHERE entity_id = :id AND action = 'CREATE'
                """).param("id", id).query().singleRow();

        assertThat(fila.get("before_values"))
                .as("la ausencia de responsable previo es lo que separa asignar de reasignar")
                .isNull();
        assertThat(fila.get("actor_id")).as("la jefa fue quien lo registro").isEqualTo(jefaId);
        assertThat(fila.get("owner_id")).as("pero el responsable es B").isEqualTo(abogadoB);
    }
}
