package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Contrato del formulario de pendientes. */
@AutoConfigureMockMvc
class PendingTaskFormContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private int pendientes() {
        return jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
    }

    @Test
    @DisplayName("un alta con solo el titulo se guarda")
    void altaMinima() throws Exception {
        mvc.perform(post("/pendientes").session(sesion).with(csrf())
                        .param("title", "Elaborar informe legal"))
                .andExpect(status().is3xxRedirection());

        assertThat(pendientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("un alta completa recupera los datos intactos")
    void altaCompleta() throws Exception {
        mvc.perform(post("/pendientes").session(sesion).with(csrf())
                .param("title", "Redactar oficio")
                .param("description", "Para la Gerencia General")
                .param("receivedAt", "2026-08-20")
                .param("scheduledFor", "2026-09-10")
                .param("deadline", "2026-09-30")
                .param("outputDocumentType", "Oficio")
                .param("outputDocumentNumber", "0123-2026")
                .param("notes", "Observacion de prueba"));

        var fila = jdbc.sql("""
                SELECT description, received_at, scheduled_for, deadline,
                       output_document_type, output_document_number, notes
                FROM pending_task WHERE title = 'Redactar oficio'
                """).query().singleRow();

        assertThat(fila.get("description")).isEqualTo("Para la Gerencia General");
        assertThat(fila.get("output_document_type")).isEqualTo("Oficio");
        assertThat(fila.get("output_document_number")).isEqualTo("0123-2026");
    }

    @Test
    @DisplayName("la fecha de registro la pone el sistema, no la persona")
    void fechaDeRegistroAutomatica() throws Exception {
        mvc.perform(post("/pendientes").session(sesion).with(csrf())
                .param("title", "Con fecha de registro"));

        Object registro = jdbc.sql("SELECT registered_at FROM pending_task")
                .query().singleRow().get("registered_at");

        assertThat(registro).as("se rellena sola con el dia del alta").isNotNull();
    }

    @Test
    @DisplayName("titulo vacio o fecha imposible no crean ningun registro")
    void datosInvalidosNoCreanRegistroParcial() throws Exception {
        String[][] casos = {
                {"", "2026-08-20"},
                {"Con fecha imposible", "2026-02-31"},
                {"Con fecha absurda", "no es una fecha"},
        };
        for (String[] caso : casos) {
            mvc.perform(post("/pendientes").session(sesion).with(csrf())
                            .param("title", caso[0])
                            .param("receivedAt", caso[1]))
                    .andExpect(status().isOk());   // vuelve al formulario
        }
        assertThat(pendientes()).isZero();
    }

    @Test
    @DisplayName("ante un error, el formulario devuelve lo que la persona escribio")
    void conservaLoEscrito() throws Exception {
        String html = mvc.perform(post("/pendientes").session(sesion).with(csrf())
                        .param("title", "")
                        .param("description", "Descripcion que no debe perderse")
                        .param("outputDocumentNumber", "0456-2026"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Descripcion que no debe perderse").contains("0456-2026");
    }

    @Test
    @DisplayName("el documento de salida se guarda como dato, no como archivo")
    void documentoDeSalidaEsUnDato() throws Exception {
        mvc.perform(post("/pendientes").session(sesion).with(csrf())
                .param("title", "Con documento")
                .param("outputDocumentType", "Informe Legal")
                .param("outputDocumentNumber", "007-2026"));

        var columnas = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'pending_task'
                """).query(String.class).list();

        // Ninguna columna guarda un fichero: la seccion 3.5 excluye documentos.
        assertThat(columnas).noneMatch(c ->
                c.contains("file") || c.contains("attachment") || c.contains("blob")
                || c.contains("archivo") || c.contains("adjunto"));
    }
}
