package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

/** Contrato del listado de pendientes: filtros, orden, estado vacio y rechazos. */
@AutoConfigureMockMvc
class PendingTaskListContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        Timestamp ahora = Timestamp.from(Instant.now());
        UUID judicial = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-LISTA-2026', true, :a, :a, 1)
                """).param("id", judicial).param("o", abogado).param("a", ahora).update();

        pendiente(abogado, "Con expediente judicial", judicial, null, "2026-12-01", true);
        pendiente(abogado, "Sin expediente ninguno", null, null, null, true);
        pendiente(abogado, "Oculto del listado", null, null, "2026-11-01", false);
    }

    private void pendiente(UUID owner, String titulo, UUID judicial, UUID administrativo,
                           String limite, boolean visible) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                          administrative_procedure_id, registered_at,
                                          deadline, active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :j, :a, :hoy, CAST(:limite AS date),
                        :visible, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", owner).param("titulo", titulo)
                .param("j", judicial).param("a", administrativo)
                .param("hoy", LocalDate.now()).param("limite", limite)
                .param("visible", visible).param("ahora", ahora).update();
    }

    private String listado(String query) throws Exception {
        return mvc.perform(get("/pendientes" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("por defecto solo se listan los visibles")
    void ocultosFueraPorDefecto() throws Exception {
        assertThat(listado("")).doesNotContain("Oculto del listado");
        assertThat(listado("?visibility=all")).contains("Oculto del listado");
    }

    @Test
    @DisplayName("se filtra por la clase de vinculo")
    void filtroPorVinculo() throws Exception {
        assertThat(listado("?linkedTo=judicial"))
                .contains("Con expediente judicial").doesNotContain("Sin expediente ninguno");
        assertThat(listado("?linkedTo=none"))
                .contains("Sin expediente ninguno").doesNotContain("Con expediente judicial");
        assertThat(listado("?linkedTo=administrative"))
                .doesNotContain("Con expediente judicial");
    }

    @Test
    @DisplayName("se filtra por presencia de fecha limite")
    void filtroPorPlazo() throws Exception {
        assertThat(listado("?deadlinePresence=without"))
                .contains("Sin expediente ninguno").doesNotContain("Con expediente judicial");
        assertThat(listado("?deadlinePresence=with"))
                .contains("Con expediente judicial");
    }

    @Test
    @DisplayName("un valor de orden no permitido se rechaza con 422")
    void ordenInvalidoSeRechaza() throws Exception {
        mvc.perform(get("/pendientes?sort=; DROP TABLE pending_task").session(sesion))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/pendientes?linkedTo=inventado").session(sesion))
                .andExpect(status().isUnprocessableEntity());

        Integer siguen = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        assertThat(siguen).isEqualTo(3);
    }

    @Test
    @DisplayName("una busqueda sin coincidencias ofrece salida")
    void estadoVacioConSalida() throws Exception {
        assertThat(listado("?q=noexisteestetitulo"))
                .contains("No hay pendientes que coincidan").contains("Quitar los filtros");
    }

    @Test
    @DisplayName("los comodines no actuan como comodin")
    void comodinesEscapados() throws Exception {
        assertThat(listado("?q=%")).contains("No hay pendientes que coincidan");
        assertThat(listado("?q=_")).contains("No hay pendientes que coincidan");
    }

    @Test
    @DisplayName("una pagina fuera de rango devuelve vacio recuperable")
    void paginaFueraDeRango() throws Exception {
        assertThat(listado("?page=99")).contains("No hay pendientes que coincidan");
    }

    @Test
    @DisplayName("el listado muestra las columnas que pide el insumo")
    void columnasDelInsumo() throws Exception {
        String html = listado("");
        assertThat(html).contains("Titulo").contains("Expediente").contains("Tipo")
                .contains("Prioridad").contains("Estado").contains("Programada")
                .contains("Fecha limite").contains("Antiguedad");
    }
}
