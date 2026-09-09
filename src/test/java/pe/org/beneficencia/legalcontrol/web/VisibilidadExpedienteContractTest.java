package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * Las fichas de expediente por fin ofrecen ocultar y volver a mostrar (RF-007).
 *
 * <p>Las rutas existen en el servidor desde las funcionalidades 001 y 002, con permiso,
 * bloqueo por version y registro en el historial. Lo que faltaba era una pantalla que
 * las usara: los listados ofrecian un filtro «Ocultos» para un estado que nada podia
 * producir.
 */
@AutoConfigureMockMvc
class VisibilidadExpedienteContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession mia;
    private UUID yo;
    private UUID mio;
    private UUID ajeno;
    private UUID procedimiento;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        UUID otra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        mia = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        mio = judicial(yo, "EXP-MIO-2026");
        ajeno = judicial(otra, "EXP-AJENO-2026");
        procedimiento = administrativo(yo, "ADM-MIO-2026");
    }

    private UUID judicial(UUID owner, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", owner).param("n", numero)
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

    private long version(String tabla, UUID id) {
        return jdbc.sql("SELECT version FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    private boolean visible(String tabla, UUID id) {
        return jdbc.sql("SELECT active FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(Boolean.class).single();
    }

    @Test
    @DisplayName("la ficha judicial ofrece el control a quien puede editar (RF-007)")
    void laFichaLoOfrece() throws Exception {
        assertThat(mvc.perform(get("/judiciales/" + mio).session(mia))
                        .andReturn().getResponse().getContentAsString())
                .as("es la responsable: el servidor se lo permite, la pantalla tambien")
                .contains("Ocultar del listado");
    }

    @Test
    @DisplayName("a quien no puede editar no se le pinta el control")
    void aQuienNoPuedeNoSeLePinta() throws Exception {
        assertThat(mvc.perform(get("/judiciales/" + ajeno).session(mia))
                        .andReturn().getResponse().getContentAsString())
                .doesNotContain("Ocultar del listado");
    }

    @Test
    @DisplayName("ocultar y volver a mostrar, con su rastro")
    void ocultarYVolver() throws Exception {
        mvc.perform(post("/judiciales/" + mio + "/visibilidad").with(csrf())
                        .param("active", "false")
                        .param("version", String.valueOf(version("judicial_case", mio)))
                        .session(mia))
                .andExpect(status().is3xxRedirection());

        assertThat(visible("judicial_case", mio)).isFalse();
        assertThat(mvc.perform(get("/judiciales?visibility=inactive").session(mia))
                        .andReturn().getResponse().getContentAsString())
                .contains("EXP-MIO-2026");

        // Y la ficha ahora ofrece lo contrario.
        assertThat(mvc.perform(get("/judiciales/" + mio).session(mia))
                        .andReturn().getResponse().getContentAsString())
                .contains("Volver a mostrar en el listado");

        assertThat(jdbc.sql("""
                SELECT count(*) FROM audit_event
                WHERE entity_type = 'JUDICIAL_CASE' AND entity_id = :id AND action = 'VISIBILITY'
                """).param("id", mio).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("la ficha administrativa hace lo mismo")
    void enLaAdministrativa() throws Exception {
        assertThat(mvc.perform(get("/administrativos/" + procedimiento).session(mia))
                        .andReturn().getResponse().getContentAsString())
                .contains("Ocultar del listado");

        mvc.perform(post("/administrativos/" + procedimiento + "/visibilidad").with(csrf())
                        .param("active", "false")
                        .param("version",
                                String.valueOf(version("administrative_procedure", procedimiento)))
                        .session(mia))
                .andExpect(status().is3xxRedirection());

        assertThat(visible("administrative_procedure", procedimiento)).isFalse();
    }

    @Test
    @DisplayName("el estado procesal «Archivado» y la visibilidad son ejes distintos")
    void archivadoNoEsOculto() throws Exception {
        // Lo advierten los comentarios de la V4 y la V8: que el estado sea «Archivado»
        // NO implica ocultarlo. Confundirlos haria desaparecer del listado corriente
        // expedientes que solo cambiaron de fase.
        UUID estado = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO procedural_status (id, name, enabled, created_by,
                                               created_at, updated_at, version)
                VALUES (:id, 'Archivado', true, :o, :ts, :ts, 1)
                """).param("id", estado).param("o", yo).param("ts", ahora).update();
        jdbc.sql("UPDATE judicial_case SET procedural_status_id = :e WHERE id = :id")
                .param("e", estado).param("id", mio).update();

        assertThat(visible("judicial_case", mio))
                .as("cambiar de estado no toca la visibilidad")
                .isTrue();
        assertThat(mvc.perform(get("/judiciales").session(mia))
                        .andReturn().getResponse().getContentAsString())
                .as("y sigue apareciendo en el listado corriente")
                .contains("EXP-MIO-2026");
    }
}
