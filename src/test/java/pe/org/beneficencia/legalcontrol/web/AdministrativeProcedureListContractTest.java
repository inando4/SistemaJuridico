package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Contrato del listado administrativo: filtros, orden, estado vacio y rechazo de
 * parametros fuera de lista cerrada.
 */
@AutoConfigureMockMvc
class AdministrativeProcedureListContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID otroAbogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        otroAbogado = SesionDePrueba.crearCuenta(jdbc, encoder, "otro@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        procedimiento("ADM-AAA-2026", "Gerencia General", "2026-01-15", true);
        procedimiento("ADM-BBB-2026", "Contabilidad", null, true);
        procedimiento("ADM-CCC-2026", "Logistica", "2030-12-31", false);
    }

    private void procedimiento(String numero, String area, String limite, boolean visible) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, requesting_area,
                                                      deadline, active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :area, CAST(:limite AS date), :visible,
                        :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", otroAbogado)
                .param("numero", numero).param("area", area)
                .param("limite", limite).param("visible", visible)
                .param("ahora", ahora).update();
    }

    private String listado(String query) throws Exception {
        return mvc.perform(get("/administrativos" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("se ven los procedimientos de otros responsables")
    void visibilidadCompartida() throws Exception {
        assertThat(listado("")).contains("ADM-AAA-2026").contains("ADM-BBB-2026");
    }

    @Test
    @DisplayName("por defecto solo se listan los visibles")
    void ocultosFueraPorDefecto() throws Exception {
        assertThat(listado("")).doesNotContain("ADM-CCC-2026");
        assertThat(listado("?visibility=all")).contains("ADM-CCC-2026");
    }

    @Test
    @DisplayName("la busqueda filtra por numero y por area solicitante")
    void busquedaYFiltros() throws Exception {
        assertThat(listado("?q=AAA")).contains("ADM-AAA-2026").doesNotContain("ADM-BBB-2026");
        assertThat(listado("?requestingArea=Contabilidad"))
                .contains("ADM-BBB-2026").doesNotContain("ADM-AAA-2026");
        assertThat(listado("?deadlinePresence=without"))
                .contains("ADM-BBB-2026").doesNotContain("ADM-AAA-2026");
    }

    @Test
    @DisplayName("los procedimientos sin fecha limite van al final al ordenar por plazo")
    void nulosAlFinal() throws Exception {
        String html = listado("?sort=deadline&direction=asc");
        assertThat(html.indexOf("ADM-AAA-2026")).isLessThan(html.indexOf("ADM-BBB-2026"));
    }

    @Test
    @DisplayName("una busqueda sin coincidencias ofrece salida, no un callejon")
    void estadoVacioConSalida() throws Exception {
        String html = listado("?q=noexisteestenumero");
        assertThat(html).contains("No hay procedimientos que coincidan")
                .contains("Quitar los filtros");
    }

    @Test
    @DisplayName("un valor de orden no permitido se rechaza con 422")
    void ordenInvalidoSeRechaza() throws Exception {
        mvc.perform(get("/administrativos?sort=; DROP TABLE administrative_procedure").session(sesion))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/administrativos?visibility=inventado").session(sesion))
                .andExpect(status().isUnprocessableEntity());

        Integer siguen = jdbc.sql("SELECT count(*) FROM administrative_procedure")
                .query(Integer.class).single();
        assertThat(siguen).isEqualTo(3);
    }

    @Test
    @DisplayName("una pagina fuera de rango devuelve vacio recuperable, no un error")
    void paginaFueraDeRango() throws Exception {
        assertThat(listado("?page=99")).contains("No hay procedimientos que coincidan");
    }

    @Test
    @DisplayName("los caracteres comodin en la busqueda no actuan como comodin")
    void comodinesEscapados() throws Exception {
        assertThat(listado("?q=%")).contains("No hay procedimientos que coincidan");
        assertThat(listado("?q=_")).contains("No hay procedimientos que coincidan");
    }

    @Test
    @DisplayName("el listado muestra las columnas que pide el insumo")
    void columnasDelInsumo() throws Exception {
        String html = listado("");
        assertThat(html)
                .contains("Responsable").contains("N.º de expediente")
                .contains("Área solicitante").contains("Pedido").contains("Estado")
                .contains("Recepción").contains("Fecha límite").contains("Observaciones");
    }
}
