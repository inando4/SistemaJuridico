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

/**
 * Contrato del buscador global (insumo, seccion 34).
 *
 * <p>Comprueba los tres estados que la pantalla distingue —sin termino, termino
 * corto y sin resultados— porque confundirlos hace creer que el sistema no tiene un
 * expediente que si tiene.
 */
@AutoConfigureMockMvc
class BusquedaContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID abogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");
    }

    private void expediente(String numero, String materia, String observaciones) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject, notes,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :materia, :notas, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("numero", numero).param("materia", materia)
                .param("notas", observaciones).param("ahora", ahora).update();
    }

    private void pendiente(String titulo, String observaciones) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, notes, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :notas, :hoy, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("titulo", titulo).param("notas", observaciones)
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private String buscar(String query) throws Exception {
        return mvc.perform(get("/buscar" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("sin termino se ofrece el buscador, no un «no se encontro nada»")
    void sinTerminoNoEsSinResultados() throws Exception {
        String html = buscar("");

        assertThat(html).contains("Buscar en judiciales");
        assertThat(html)
                .as("la pantalla de partida no puede parecer una respuesta negativa")
                .doesNotContain("No hay ningún expediente judicial");
    }

    @Test
    @DisplayName("un termino demasiado corto avisa y no consulta")
    void terminoCortoAvisa() throws Exception {
        expediente("EXP-AAA-2026", "Desalojo", null);

        String html = buscar("?q=de");

        assertThat(html).contains("Escriba al menos");
        assertThat(html)
                .as("con «de» el comodin coincidiria con casi todo; no debe consultarse")
                .doesNotContain("EXP-AAA-2026");
    }

    @Test
    @DisplayName("el termino se busca en los tres tipos y se agrupa")
    void tresGrupos() throws Exception {
        expediente("EXP-SRV-2026", "Servidumbre de paso", null);
        pendiente("Revisar servidumbre", null);

        String html = buscar("?q=servidumbre");

        assertThat(html).contains("Judiciales").contains("Pendientes");
        assertThat(html).contains("EXP-SRV-2026").contains("Revisar servidumbre");
    }

    @Test
    @DisplayName("los comodines del termino son texto literal")
    void comodinesEscapados() throws Exception {
        expediente("EXP-AAA-2026", "Desalojo", null);
        expediente("EXP-100-2026", "Cobro del 100% del saldo", null);

        // El termino va por param y no en la cadena: MockMvc no decodifica el
        // porcentaje de la URL, y «100%25» llegaria tal cual al controlador.
        String html = mvc.perform(get("/buscar").param("q", "100%").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Sin escape, «100%» acabaria como «%100%%» y coincidiria con todo lo que
        // empiece por 100; con escape, solo con el que lo contiene literalmente.
        assertThat(html).contains("EXP-100-2026").doesNotContain("EXP-AAA-2026");
    }

    @Test
    @DisplayName("un termino sin coincidencias lo dice")
    void sinResultadosLoDice() throws Exception {
        expediente("EXP-AAA-2026", "Desalojo", null);

        assertThat(buscar("?q=noexisteestapalabra"))
                .contains("No hay ningún expediente judicial");
    }

    @Test
    @DisplayName("sin sesion se va al acceso, no a los resultados")
    void sinSesionSeRechaza() throws Exception {
        // El filtro de sesion actua antes que el controlador, asi que la respuesta es
        // la redireccion al acceso y no un error: el mismo trato que el resto de
        // pantallas. Lo que importa es que no devuelva resultados.
        mvc.perform(get("/buscar").param("q", "servidumbre"))
                .andExpect(status().is3xxRedirection());
    }
}
