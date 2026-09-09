package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

/** El alta que llega desde la ficha con el expediente ya elegido (insumo 28 y 30). */
@AutoConfigureMockMvc
class AltaVinculadaContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID laOtra;
    private UUID expediente;
    private UUID expedienteArchivado;
    private UUID expedienteAjeno;
    private UUID procedimiento;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        laOtra = SesionDePrueba.crearCuenta(jdbc, encoder, "colega@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Colega Inventada' WHERE id = :id")
                .param("id", laOtra).update();
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        expediente = judicial(yo, "EXP-ALTA-2026", true);
        expedienteArchivado = judicial(yo, "EXP-ARCHIVADO-2026", false);
        expedienteAjeno = judicial(laOtra, "EXP-AJENO-2026", true);
        procedimiento = administrativo(yo, "ADM-ALTA-2026");
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

    private UUID administrativo(UUID owner, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", owner).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private String alta(String query) throws Exception {
        return mvc.perform(get("/pendientes/nuevo" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("el expediente llega pre-seleccionado (RF-012)")
    void preSeleccionado() throws Exception {
        String html = alta("?judicialCaseId=" + expediente);

        assertThat(html)
                .as("la opcion del expediente sale marcada")
                .containsPattern("value=\"" + expediente + "\"[^>]*selected");
    }

    @Test
    @DisplayName("el vinculo se puede cambiar o quitar: no queda fijado (RF-013)")
    void sePuedeQuitar() throws Exception {
        String html = alta("?judicialCaseId=" + expediente);

        // Un campo oculto con desplegable deshabilitado habria fijado el vinculo.
        assertThat(html)
                .as("sigue siendo un desplegable operable, con la opcion «Ninguno»")
                .contains("<select id=\"judicialCaseId\" name=\"judicialCaseId\">")
                .contains(">Ninguno<");
        assertThat(html)
                .as("y no hay un campo oculto compitiendo por el mismo nombre")
                .doesNotContain("type=\"hidden\" name=\"judicialCaseId\"");
    }

    @Test
    @DisplayName("un expediente ARCHIVADO tambien llega como opcion (R5)")
    void expedienteArchivadoEntreLasOpciones() throws Exception {
        // El desplegable se llena con active = true, pero la ficha de un archivado se
        // abre con normalidad. Sin añadir la opcion, th:selected no encajaria, el
        // <select> enviaria vacio y el vinculo se perderia al guardar SIN dar error.
        String html = alta("?judicialCaseId=" + expedienteArchivado);

        assertThat(html)
                .contains("EXP-ARCHIVADO-2026")
                .as("y se dice que esta archivado, para que no parezca un error")
                .contains("(archivado)")
                .containsPattern("value=\"" + expedienteArchivado + "\"[^>]*selected");
    }

    @Test
    @DisplayName("el vinculo a un archivado sobrevive al guardar")
    void elVinculoAlArchivadoSeGuarda() throws Exception {
        mvc.perform(post("/pendientes").with(csrf())
                        .param("title", "Escrito de un expediente archivado")
                        .param("judicialCaseId", expedienteArchivado.toString())
                        .session(sesion))
                .andExpect(status().is3xxRedirection());

        UUID guardado = jdbc.sql("""
                SELECT judicial_case_id FROM pending_task
                WHERE title = 'Escrito de un expediente archivado'
                """).query(UUID.class).single();

        assertThat(guardado)
                .as("el fallo silencioso seria un null aqui, sin ningun error a la vista")
                .isEqualTo(expedienteArchivado);
    }

    @Test
    @DisplayName("con expediente de otra persona se avisa antes de guardar (RF-015)")
    void avisoDePropiedadAjena() throws Exception {
        String html = alta("?judicialCaseId=" + expedienteAjeno);

        assertThat(html)
                .contains("El responsable sera usted")
                .as("y ademas se dice de quien es el expediente")
                .contains("EXP-AJENO-2026")
                .contains("Colega Inventada");
    }

    @Test
    @DisplayName("con expediente propio no aparece el aviso de propiedad ajena")
    void sinAvisoCuandoEsPropio() throws Exception {
        assertThat(alta("?judicialCaseId=" + expediente))
                .as("un aviso que sale siempre deja de leerse")
                .doesNotContain("El pendiente quedará a su nombre de usted");
    }

    @Test
    @DisplayName("un expediente inexistente en el enlace se rechaza (RF-016)")
    void inexistenteEnElEnlace() throws Exception {
        mvc.perform(get("/pendientes/nuevo?judicialCaseId=" + UUID.randomUUID()).session(sesion))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un expediente inexistente en el POST da error de validacion, no 500 (RF-016)")
    void inexistenteEnElPost() throws Exception {
        // Antes de esta feature, un identificador inventado llegaba a la clave
        // foranea y salia como error 500. Desde el desplegable era inalcanzable; con
        // un parametro en la URL esta a un clic, asi que se comprueba en el servidor.
        String html = mvc.perform(post("/pendientes").with(csrf())
                        .param("title", "Con vinculo roto")
                        .param("judicialCaseId", UUID.randomUUID().toString())
                        .session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("El expediente judicial indicado no existe.");
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task WHERE title = 'Con vinculo roto'")
                .query(Integer.class).single())
                .as("y no se crea nada")
                .isZero();
    }

    @Test
    @DisplayName("los dos identificadores a la vez en el enlace se rechazan (RF-014)")
    void losDosEnElEnlace() throws Exception {
        mvc.perform(get("/pendientes/nuevo")
                        .param("judicialCaseId", expediente.toString())
                        .param("administrativeProcedureId", procedimiento.toString())
                        .session(sesion))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("el alta administrativa funciona igual")
    void altaAdministrativa() throws Exception {
        assertThat(alta("?administrativeProcedureId=" + procedimiento))
                .containsPattern("value=\"" + procedimiento + "\"[^>]*selected");
    }

    @Test
    @DisplayName("un expediente judicial no se ofrece entre los administrativos")
    void cadaOpcionEnSuLista() throws Exception {
        // Ofrecer el archivado judicial en la lista de administrativos crearia un
        // vinculo que la validacion rechazaria despues, sin que se entienda por que.
        String html = alta("?judicialCaseId=" + expedienteArchivado);

        int desde = html.indexOf("name=\"administrativeProcedureId\"");
        int hasta = html.indexOf("</select>", desde);
        assertThat(html.substring(desde, hasta))
                .as("el desplegable administrativo no contiene el expediente judicial")
                .doesNotContain(expedienteArchivado.toString());
    }

    @Test
    @DisplayName("el pendiente creado desde una ficha ajena queda a nombre de quien registra")
    void laPropiedadNoCambia() throws Exception {
        mvc.perform(post("/pendientes").with(csrf())
                        .param("title", "Registrado en expediente ajeno")
                        .param("judicialCaseId", expedienteAjeno.toString())
                        .session(sesion))
                .andExpect(status().is3xxRedirection());

        UUID responsable = jdbc.sql("""
                SELECT owner_id FROM pending_task
                WHERE title = 'Registrado en expediente ajeno'
                """).query(UUID.class).single();

        assertThat(responsable)
                .as("esta feature no cambia la regla de propiedad; solo avisa antes")
                .isEqualTo(yo);
    }
}
