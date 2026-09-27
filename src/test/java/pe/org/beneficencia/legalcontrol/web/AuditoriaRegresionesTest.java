package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.org.beneficencia.legalcontrol.config.ClockConfig;
import pe.org.beneficencia.legalcontrol.integration.DatosSinteticos;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

@AutoConfigureMockMvc
class AuditoriaRegresionesTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    MockHttpSession sesion;
    UUID jefa;
    UUID abogada;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa-audit@ejemplo.test", "HEAD");
        abogada = SesionDePrueba.crearCuenta(jdbc, encoder, "abogada-audit@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "jefa-audit@ejemplo.test");
    }

    private String html(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private UUID catalogo(String tabla) {
        return DatosSinteticos.sembrarCatalogo(jdbc, tabla, "Valor histórico", 1, jefa,
                Timestamp.from(Instant.now())).getFirst();
    }

    private void seleccionado(String html, UUID id) {
        assertThat(Pattern.compile("<option\\b(?=[^>]*value=\"" + id
                + "\")(?=[^>]*selected=\"selected\")[^>]*>").matcher(html).find()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"judiciales,caseNumber,judicial_case", "administrativos,fileNumber,administrative_procedure"})
    void errorYReintentoConservanResponsable(String ruta, String campo, String tabla) throws Exception {
        mvc.perform(post("/" + ruta).session(sesion).with(csrf()).param(campo, "EXP-DUPLICADO"))
                .andExpect(status().is3xxRedirection());
        var respuesta = mvc.perform(post("/" + ruta).session(sesion).with(csrf())
                        .param(campo, "EXP-DUPLICADO").param("ownerId", abogada.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("responsableSeleccionado", abogada)).andReturn();
        seleccionado(html(respuesta), abogada);
        mvc.perform(post("/" + ruta).session(sesion).with(csrf())
                        .param(campo, "EXP-CORREGIDO").param("ownerId", abogada.toString()))
                .andExpect(status().is3xxRedirection());
        String numero = campo.equals("caseNumber") ? "case_number" : "file_number";
        assertThat(jdbc.sql("SELECT owner_id FROM " + tabla + " WHERE " + numero + "='EXP-CORREGIDO'")
                .query(UUID.class).single()).isEqualTo(abogada);
    }

    @ParameterizedTest
    @CsvSource({"judiciales,caseNumber,judicial_case", "administrativos,fileNumber,administrative_procedure"})
    void responsableNoDisponibleSeRechazaSinSustituirlo(String ruta, String campo, String tabla) throws Exception {
        jdbc.sql("UPDATE app_user SET status='INACTIVE' WHERE id=:id").param("id", abogada).update();
        var r = mvc.perform(post("/" + ruta).session(sesion).with(csrf())
                        .param(campo, "EXP-INACTIVO").param("ownerId", abogada.toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(html(r)).contains("id=\"error-ownerId\"", "responsable activo");
        assertThat(jdbc.sql("SELECT count(*) FROM " + tabla).query(Integer.class).single()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"pending_task_type,pendingTaskTypeId,pending_task_type_id",
            "priority,priorityId,priority_id", "pending_task_status,pendingTaskStatusId,pending_task_status_id"})
    void editarConservaCatalogosRetirados(String tabla, String campo, String columna) throws Exception {
        UUID valor = catalogo(tabla);
        var alta = mvc.perform(post("/pendientes").session(sesion).with(csrf())
                        .param("title", "Pendiente histórico").param(campo, valor.toString()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String ficha = alta.getResponse().getRedirectedUrl();
        UUID id = UUID.fromString(ficha.substring(ficha.lastIndexOf('/') + 1));
        jdbc.sql("UPDATE " + tabla + " SET enabled=false WHERE id=:id").param("id", valor).update();
        String edicion = html(mvc.perform(get(ficha + "/editar").session(sesion))
                .andExpect(status().isOk()).andReturn());
        seleccionado(edicion, valor);
        assertThat(edicion).contains("(deshabilitado)");
        var error = mvc.perform(post(ficha).session(sesion).with(csrf())
                        .param("title", "").param(campo, valor.toString()).param("version", "1"))
                .andExpect(status().isOk()).andReturn();
        seleccionado(html(error), valor);
        mvc.perform(post(ficha).session(sesion).with(csrf()).param("title", "Pendiente histórico")
                        .param(campo, valor.toString()).param("notes", "Cambio sin pérdida").param("version", "1"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.sql("SELECT " + columna + " FROM pending_task WHERE id=:id")
                .param("id", id).query(UUID.class).single()).isEqualTo(valor);
        var rechazo = mvc.perform(post("/pendientes").session(sesion).with(csrf())
                        .param("title", "Nueva asociación inválida").param(campo, valor.toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(html(rechazo)).contains("id=\"error-" + campo + "\"");
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"judiciales,caseNumber,procedural_status,proceduralStatusId", "administrativos,fileNumber,administrative_status,administrativeStatusId"})
    void estadosHistoricosTambienSeConservanEnExpedientes(String ruta, String numero, String tabla, String campo) throws Exception {
        UUID valor = catalogo(tabla);
        var alta = mvc.perform(post("/" + ruta).session(sesion).with(csrf())
                .param(numero, "EXP-HISTORICO").param(campo, valor.toString())).andReturn();
        String ficha = alta.getResponse().getRedirectedUrl();
        jdbc.sql("UPDATE " + tabla + " SET enabled=false WHERE id=:id").param("id", valor).update();
        seleccionado(html(mvc.perform(get(ficha + "/editar").session(sesion)).andReturn()), valor);
        mvc.perform(post(ficha).session(sesion).with(csrf()).param(numero, "EXP-HISTORICO")
                        .param(campo, valor.toString()).param("notes", "Solo observaciones").param("version", "1"))
                .andExpect(status().is3xxRedirection());
        seleccionado(html(mvc.perform(get(ficha + "/editar").session(sesion)).andReturn()), valor);
    }

    @Test
    void correccionInvalidaPermaneceEnLaMismaActividad() throws Exception {
        UUID tipo = catalogo("pending_task_type");
        String dia = LocalDate.now(ClockConfig.ZONA).toString();
        mvc.perform(post("/actividad-diaria").session(sesion).with(csrf())
                .param("description", "Original").param("performedOn", dia).param("typeId", tipo.toString()));
        UUID id = jdbc.sql("SELECT id FROM manual_activity").query(UUID.class).single();
        String ruta = "/actividad-diaria/" + id + "/editar";
        var error = mvc.perform(post(ruta).session(sesion).with(csrf()).param("description", "Corrección")
                        .param("performedOn", dia).param("typeId", tipo.toString())
                        .param("otherType", "Otro").param("version", "1"))
                .andExpect(status().isOk()).andExpect(model().attribute("edicionId", id)).andReturn();
        assertThat(html(error)).contains("open=\"open\"", "id=\"" + id + "-description\"", "Corrección");
        var alta = (pe.org.beneficencia.legalcontrol.activity.ManualActivityForm) error.getModelAndView().getModel().get("form");
        assertThat(alta.description()).isNull();
        mvc.perform(post(ruta).session(sesion).with(csrf()).param("description", "Corrección")
                        .param("performedOn", dia).param("otherType", "Otro").param("version", "1"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.sql("SELECT description FROM manual_activity").query(String.class).list())
                .containsExactly("Corrección");
    }

    @ParameterizedTest
    @CsvSource({"description,10001", "notes,10001", "outputDocumentType,151", "outputDocumentNumber,151"})
    void todoErrorDeLongitudSeIdentificaYAsociaAlCampo(String campo, int longitud) throws Exception {
        String texto = "A".repeat(longitud);
        String pagina = html(mvc.perform(post("/pendientes").session(sesion).with(csrf())
                        .param("title", "No guardar").param(campo, texto))
                .andExpect(status().isOk()).andReturn());
        assertThat(pagina).contains("id=\"error-" + campo + "\"", "aria-describedby=\"error-" + campo + "\"",
                "aria-invalid=\"true\"", "caracteres permitidos", texto);
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single()).isZero();
    }

    @Test
    void filtrosConEspaciosSobrevivenAFichaYEdicion() throws Exception {
        String ficha = mvc.perform(post("/pendientes").session(sesion).with(csrf())
                        .param("title", "Revisión legal"))
                .andReturn().getResponse().getRedirectedUrl();
        String consulta = "?q=Revisi%C3%B3n+legal&sort=title&page=2";
        String pagina = html(mvc.perform(get(ficha).session(sesion).param("volver", consulta))
                .andExpect(status().isOk()).andReturn());
        assertThat(pagina).contains("href=\"/pendientes?q=Revisi%C3%B3n+legal&amp;sort=title&amp;page=2\"");
        var r = mvc.perform(post(ficha).session(sesion).with(csrf()).param("title", "Revisión legal")
                        .param("version", "1").param("volver", consulta))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).contains("?volver=%3Fq%3D");
    }

    @Test
    void paginaExtremaEsUnListadoVacioControlado() throws Exception {
        mvc.perform(get("/pendientes").session(sesion).param("page", "100000000"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 409, 422, 429, 500})
    void erroresGeneralesSonLocalesYConservanEstadoHttp(int codigo) throws Exception {
        var r = mvc.perform(get("/error").with(rq -> { rq.setDispatcherType(DispatcherType.ERROR); return rq; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, codigo))
                .andExpect(status().is(codigo)).andReturn();
        assertThat(html(r)).contains("lang=\"es\"", "Volver al inicio")
                .doesNotContain("Whitelabel", "java.lang.", "SQL");
    }
}
