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
 * Los cuatro valores de visibilidad producen cuatro conjuntos distintos.
 *
 * <p>Lo que importa aqui no es cada uno por separado sino que se diferencien: si
 * dos coincidieran, uno de los dos sobraria y alguna pantalla estaria enseñando
 * algo distinto de lo que cree.
 */
@AutoConfigureMockMvc
class PendingTaskVisibilityContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    private static final String POR_HACER = "Por hacer y visible";
    private static final String CUMPLIDO = "Ya cumplido pero visible";
    private static final String ARCHIVADO = "Retirado del listado";

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        pendiente(abogado, POR_HACER, true, false);
        pendiente(abogado, CUMPLIDO, true, true);
        pendiente(abogado, ARCHIVADO, false, false);
    }

    private void pendiente(UUID owner, String titulo, boolean visible, boolean cumplido) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, completed_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :o, :t, :hoy, :fin, :vis, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("hoy", LocalDate.now())
                .param("fin", cumplido ? ahora : null)
                .param("vis", visible).param("ts", ahora).update();
    }

    private String listado(String query) throws Exception {
        return mvc.perform(get("/pendientes" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("active: solo lo que queda por hacer")
    void active() throws Exception {
        assertThat(listado("?visibility=active"))
                .contains(POR_HACER)
                .as("lo cumplido tiene su propia pantalla; aqui estorbaria")
                .doesNotContain(CUMPLIDO)
                .doesNotContain(ARCHIVADO);
    }

    @Test
    @DisplayName("notArchived: lo no archivado, cumplidos incluidos (R3)")
    void notArchived() throws Exception {
        // Es el conjunto de la ficha del expediente: lo cumplido es su historia y
        // se queda; lo retirado se quito a proposito y no vuelve.
        assertThat(listado("?visibility=notArchived"))
                .contains(POR_HACER)
                .contains(CUMPLIDO)
                .doesNotContain(ARCHIVADO);
    }

    @Test
    @DisplayName("inactive: solo lo archivado")
    void inactive() throws Exception {
        assertThat(listado("?visibility=inactive"))
                .contains(ARCHIVADO)
                .doesNotContain(POR_HACER);
    }

    @Test
    @DisplayName("all: todo")
    void all() throws Exception {
        assertThat(listado("?visibility=all"))
                .contains(POR_HACER)
                .contains(CUMPLIDO)
                .contains(ARCHIVADO);
    }

    @Test
    @DisplayName("notArchived no coincide con ninguno de los otros tres")
    void notArchivedEsUnConjuntoPropio() throws Exception {
        String conNotArchived = listado("?visibility=notArchived");

        // Esta es la razon de existir del cuarto valor. Sin el, la ficha del
        // expediente tendria que elegir entre esconder sus cumplidos (con «active»)
        // o mostrar archivados que habia ocultado (con «all»), y su enlace «Ver
        // todos» llevaria a un conjunto distinto del que el usuario acababa de ver.
        assertThat(conNotArchived)
                .as("se distingue de active porque incluye el cumplido")
                .contains(CUMPLIDO);
        assertThat(conNotArchived)
                .as("se distingue de all porque excluye el archivado")
                .doesNotContain(ARCHIVADO);
    }

    @Test
    @DisplayName("una visibilidad inventada se rechaza")
    void visibilidadInventada() throws Exception {
        mvc.perform(get("/pendientes?visibility=inventada").session(sesion))
                .andExpect(status().isUnprocessableEntity());
    }
}
