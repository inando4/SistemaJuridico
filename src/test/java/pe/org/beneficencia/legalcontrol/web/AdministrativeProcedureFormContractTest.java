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

/**
 * Contrato del formulario de alta administrativo.
 *
 * <p>Lo que de verdad se comprueba: que un dato invalido <b>no deja un registro a
 * medias</b> y que el formulario vuelve con lo que la persona habia escrito.
 */
@AutoConfigureMockMvc
class AdministrativeProcedureFormContractTest extends PostgresIntegrationTest {

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

    private int procedimientos() {
        return jdbc.sql("SELECT count(*) FROM administrative_procedure")
                .query(Integer.class).single();
    }

    @Test
    @DisplayName("un alta con solo el numero de expediente se guarda")
    void altaMinima() throws Exception {
        mvc.perform(post("/administrativos").session(sesion).with(csrf())
                        .param("fileNumber", "ADM-0001-2026"))
                .andExpect(status().is3xxRedirection());

        assertThat(procedimientos()).isEqualTo(1);
    }

    @Test
    @DisplayName("un alta completa recupera los datos intactos")
    void altaCompleta() throws Exception {
        mvc.perform(post("/administrativos").session(sesion).with(csrf())
                .param("fileNumber", "ADM-0002-2026")
                .param("requestingArea", "Gerencia General")
                .param("request", "Opinion legal sobre convenio de cesion")
                .param("receivedAt", "2026-08-01")
                .param("deadline", "2026-12-31")
                .param("notes", "Observacion de prueba"));

        var fila = jdbc.sql("""
                SELECT requesting_area, request, received_at, deadline, notes
                FROM administrative_procedure WHERE file_number = 'ADM-0002-2026'
                """).query().singleRow();

        assertThat(fila.get("requesting_area")).isEqualTo("Gerencia General");
        assertThat(fila.get("request")).isEqualTo("Opinion legal sobre convenio de cesion");
        assertThat(fila.get("notes")).isEqualTo("Observacion de prueba");
    }

    @Test
    @DisplayName("el numero conserva ceros iniciales, guiones y mayusculas")
    void numeroSeConservaTalCual() throws Exception {
        mvc.perform(post("/administrativos").session(sesion).with(csrf())
                .param("fileNumber", "00457-2026-SBA-GG"));

        String guardado = jdbc.sql("SELECT file_number FROM administrative_procedure")
                .query(String.class).single();
        assertThat(guardado).isEqualTo("00457-2026-SBA-GG");
    }

    @Test
    @DisplayName("numero vacio o fecha imposible no crean ningun registro")
    void datosInvalidosNoCreanRegistroParcial() throws Exception {
        String[][] casos = {
                {"", "2026-08-01"},
                {"ADM-X", "2026-02-31"},
                {"ADM-Y", "no es una fecha"},
        };
        for (String[] caso : casos) {
            mvc.perform(post("/administrativos").session(sesion).with(csrf())
                            .param("fileNumber", caso[0])
                            .param("receivedAt", caso[1]))
                    .andExpect(status().isOk());   // vuelve al formulario, no redirige
        }
        assertThat(procedimientos()).as("ningun dato invalido debe dejar registro").isZero();
    }

    @Test
    @DisplayName("ante un error, el formulario devuelve lo que la persona escribio")
    void conservaLoEscrito() throws Exception {
        String html = mvc.perform(post("/administrativos").session(sesion).with(csrf())
                        .param("fileNumber", "")
                        .param("requestingArea", "Contabilidad")
                        .param("request", "Informe sobre deuda"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Contabilidad").contains("Informe sobre deuda");
    }

    @Test
    @DisplayName("una fecha limite anterior a la de recepcion se guarda y se advierte")
    void fechasIncoherentesSeGuardanConAviso() throws Exception {
        mvc.perform(post("/administrativos").session(sesion).with(csrf())
                        .param("fileNumber", "ADM-INCOHERENTE-2026")
                        .param("receivedAt", "2026-08-01")
                        .param("deadline", "2026-07-01"))
                .andExpect(status().is3xxRedirection());

        assertThat(procedimientos()).as("se guarda: el sistema avisa pero no corrige").isEqualTo(1);

        var fila = jdbc.sql("""
                SELECT received_at, deadline FROM administrative_procedure
                WHERE file_number = 'ADM-INCOHERENTE-2026'
                """).query().singleRow();
        assertThat(fila.get("received_at").toString()).isEqualTo("2026-08-01");
        assertThat(fila.get("deadline").toString()).as("no se corrige ninguna fecha")
                .isEqualTo("2026-07-01");
    }
}
