package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import pe.org.beneficencia.legalcontrol.config.ClockConfig;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/** Contrato de la pantalla de actividad diaria: parametros, validacion y CSRF. */
@AutoConfigureMockMvc
class ActividadDiariaContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID abogado;
    private UUID otro;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        otro = SesionDePrueba.crearCuenta(jdbc, encoder, "otro@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");
    }

    private LocalDate hoy() {
        return LocalDate.now(ClockConfig.ZONA);
    }

    private int actividades() {
        return jdbc.sql("SELECT count(*) FROM manual_activity").query(Integer.class).single();
    }

    @Test
    @DisplayName("una fecha ilegible no es un error: se muestra hoy")
    void fechaIlegibleMuestraHoy() throws Exception {
        String html = mvc.perform(get("/actividad-diaria?dia=nodeberia").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("perder los filtros por un parametro mal escrito castiga a quien lo escribio")
                .contains(hoy().toString());
    }

    @Test
    @DisplayName("registrar una actividad manual redirige a su dia")
    void altaCorrectaRedirige() throws Exception {
        mvc.perform(post("/actividad-diaria").with(csrf()).session(sesion)
                        .param("description", "Atencion al publico")
                        .param("performedOn", hoy().toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(actividades()).isEqualTo(1);
    }

    @Test
    @DisplayName("una fecha futura se rechaza y no escribe nada")
    void fechaFuturaNoEscribe() throws Exception {
        String html = mvc.perform(post("/actividad-diaria").with(csrf()).session(sesion)
                        .param("description", "Lo hare manana")
                        .param("performedOn", hoy().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("No se puede registrar actividad de un día futuro");
        assertThat(actividades()).as("una validacion fallida no deja rastro").isZero();
    }

    @Test
    @DisplayName("una descripcion en blanco se rechaza")
    void descripcionObligatoria() throws Exception {
        mvc.perform(post("/actividad-diaria").with(csrf()).session(sesion)
                        .param("description", "   ")
                        .param("performedOn", hoy().toString()))
                .andExpect(status().isOk());

        assertThat(actividades()).isZero();
    }

    @Test
    @DisplayName("el POST exige CSRF")
    void altaExigeCsrf() throws Exception {
        mvc.perform(post("/actividad-diaria").session(sesion)
                        .param("description", "Sin token")
                        .param("performedOn", hoy().toString()))
                .andExpect(status().isForbidden());

        assertThat(actividades()).isZero();
    }

    @Test
    @DisplayName("retirar la actividad de otro se rechaza en el servidor")
    void retirarLaDeOtroSeRechaza() throws Exception {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             created_at, updated_at, version)
                VALUES (:id, :owner, :dia, 'Parte de otra persona', :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", otro).param("dia", hoy())
                .param("ahora", ahora).update();

        // Enviado directamente, sin pasar por la interfaz: ocultar el boton no es
        // autorizacion, y esta es la comprobacion que lo demuestra.
        mvc.perform(post("/actividad-diaria/" + id + "/retirar").with(csrf()).session(sesion)
                        .param("version", "1"))
                .andExpect(status().is4xxClientError());

        Boolean sigueActiva = jdbc.sql("SELECT active FROM manual_activity WHERE id = :id")
                .param("id", id).query(Boolean.class).single();
        assertThat(sigueActiva).isTrue();
    }

    @Test
    @DisplayName("el formulario de alta no sale al mirar la actividad de otro")
    void sinFormularioEnLaAjena() throws Exception {
        String html = mvc.perform(get("/actividad-diaria?ownerId=" + otro).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("Agregar actividad manual");
    }
}
