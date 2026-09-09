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
 * Contrato del listado: filtros, orden, estado vacio y rechazo de parametros.
 *
 * <p>La propiedad central es que <b>todos ven los expedientes de todos</b>: la
 * visibilidad compartida no depende del rol ni de quien sea el responsable.
 */
@AutoConfigureMockMvc
class JudicialCaseListContractTest extends PostgresIntegrationTest {

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

        expediente("EXP-AAA-2026", otroAbogado, "Desalojo", "2026-01-15", true);
        expediente("EXP-BBB-2026", otroAbogado, "Cobro de soles", null, true);
        expediente("EXP-CCC-2026", otroAbogado, "Desalojo", "2030-12-31", false);
    }

    private void expediente(String numero, UUID responsable, String materia,
                            String limite, boolean visible) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject, deadline,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :materia, CAST(:limite AS date), :visible,
                        :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", responsable)
                .param("numero", numero).param("materia", materia)
                .param("limite", limite).param("visible", visible)
                .param("ahora", ahora).update();
    }

    private String listado(String query) throws Exception {
        return mvc.perform(get("/judiciales" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("se ven los expedientes de otros responsables")
    void visibilidadCompartida() throws Exception {
        String html = listado("");
        assertThat(html).contains("EXP-AAA-2026").contains("EXP-BBB-2026");
    }

    @Test
    @DisplayName("por defecto solo se listan los visibles")
    void ocultosFueraPorDefecto() throws Exception {
        assertThat(listado("")).doesNotContain("EXP-CCC-2026");
        assertThat(listado("?visibility=all")).contains("EXP-CCC-2026");
        assertThat(listado("?visibility=inactive")).contains("EXP-CCC-2026")
                .doesNotContain("EXP-AAA-2026");
    }

    @Test
    @DisplayName("la busqueda filtra por numero y los filtros se combinan")
    void busquedaYCombinacion() throws Exception {
        assertThat(listado("?q=AAA")).contains("EXP-AAA-2026").doesNotContain("EXP-BBB-2026");
        assertThat(listado("?deadlinePresence=without")).contains("EXP-BBB-2026")
                .doesNotContain("EXP-AAA-2026");
        assertThat(listado("?visibility=all&deadlinePresence=with&q=CCC"))
                .contains("EXP-CCC-2026").doesNotContain("EXP-AAA-2026");
    }

    @Test
    @DisplayName("los expedientes sin fecha limite van al final al ordenar por plazo")
    void nulosAlFinal() throws Exception {
        String html = listado("?sort=deadline&direction=asc");
        assertThat(html.indexOf("EXP-AAA-2026")).isLessThan(html.indexOf("EXP-BBB-2026"));
    }

    /** Un expediente cuyo unico rastro del termino esta en el campo indicado. */
    private void expedienteCon(String numero, String materia, String observaciones) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject, notes,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :materia, :notas, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", otroAbogado)
                .param("numero", numero).param("materia", materia)
                .param("notas", observaciones).param("ahora", ahora).update();
    }

    @Test
    @DisplayName("la busqueda cubre materia y observaciones (insumo 34)")
    void busquedaPorMateriaYObservaciones() throws Exception {
        // El termino no esta en el numero, ni en el demandante, ni en el demandado:
        // solo en el campo que se acaba de anadir. Si la condicion no lo incluye, el
        // expediente no aparece y el fallo es invisible con datos normales.
        expedienteCon("EXP-MAT-2026", "Servidumbre de paso", null);
        expedienteCon("EXP-OBS-2026", "Desalojo", "Se coordino con servidumbre vecinal");

        assertThat(listado("?q=servidumbre"))
                .as("materia y observaciones son campos que el insumo enumera")
                .contains("EXP-MAT-2026").contains("EXP-OBS-2026");
    }

    @Test
    @DisplayName("una busqueda sin coincidencias ofrece salida, no un callejon")
    void estadoVacioConSalida() throws Exception {
        String html = listado("?q=noexisteestenumero");
        assertThat(html).contains("No hay expedientes que coincidan")
                .contains("Quitar los filtros");
    }

    @Test
    @DisplayName("un valor de orden no permitido se rechaza con 422")
    void ordenInvalidoSeRechaza() throws Exception {
        mvc.perform(get("/judiciales?sort=; DROP TABLE judicial_case").session(sesion))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/judiciales?visibility=inventado").session(sesion))
                .andExpect(status().isUnprocessableEntity());

        Integer siguen = jdbc.sql("SELECT count(*) FROM judicial_case").query(Integer.class).single();
        assertThat(siguen).isEqualTo(3);
    }

    @Test
    @DisplayName("una pagina fuera de rango devuelve vacio recuperable, no un error")
    void paginaFueraDeRango() throws Exception {
        String html = listado("?page=99");
        assertThat(html).contains("No hay expedientes que coincidan");
    }

    @Test
    @DisplayName("los caracteres comodin en la busqueda no actuan como comodin")
    void comodinesEscapados() throws Exception {
        assertThat(listado("?q=%")).contains("No hay expedientes que coincidan");
        assertThat(listado("?q=_")).contains("No hay expedientes que coincidan");
    }

    @Test
    @DisplayName("el listado muestra las columnas que pide el insumo (seccion 28)")
    void columnasDelInsumo() throws Exception {
        String html = listado("");

        assertThat(html)
                .contains("N.º de expediente").contains("Demandante").contains("Demandado")
                .contains("Materia").contains("Estado procesal").contains("Responsable")
                .contains("Fecha límite");
    }

    @Test
    @DisplayName("el listado ofrece los filtros que enumera la seccion 27")
    void filtrosDelInsumo() throws Exception {
        // Esta prueba es la que faltaba: las otras dos listas tenian su
        // «columnasDelInsumo» y esta no, y por eso la seccion 27 pudo pedir ocho
        // filtros mientras la pantalla ofrecia tres sin que nada lo delatara.
        String html = listado("");

        assertThat(html)
                .as("abogado responsable").contains("name=\"ownerId\"")
                .as("estado procesal").contains("name=\"proceduralStatusId\"")
                .as("materia").contains("name=\"subject\"")
                .as("vencidos").contains("name=\"overdue\"")
                .as("con y sin fecha limite").contains("name=\"deadlinePresence\"");
    }
}
