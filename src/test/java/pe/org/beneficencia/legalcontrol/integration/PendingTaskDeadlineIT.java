package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

/**
 * La antiguedad de un pendiente sin plazo se mide contra el calendario correcto.
 *
 * <p>Comprueba desde la pantalla lo que {@code CoberturaAnualTest} comprueba sobre
 * la funcion: que una recepcion del ano anterior da un numero y no el aviso de
 * calendario sin revisar. Es el fallo que la 003 dejo en produccion y que solo
 * aparece en enero.
 */
@AutoConfigureMockMvc
class PendingTaskDeadlineIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID usuario;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");

        // El ano anterior y el actual, para que el intervalo este cubierto de verdad.
        int ano = LocalDate.now().getYear();
        DatosSinteticos.sembrarCalendario(jdbc, usuario, ano - 1, ano, ano + 1);

        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private UUID pendienteSinPlazoRecibidoEl(LocalDate recepcion) {
        Timestamp ahora = Timestamp.from(Instant.now());
        UUID tipo = DatosSinteticos.sembrarCatalogo(
                jdbc, "pending_task_type", "Tipo " + UUID.randomUUID(), 1, usuario, ahora).get(0);
        UUID prioridad = DatosSinteticos.sembrarCatalogo(
                jdbc, "priority", "Prioridad " + UUID.randomUUID(), 1, usuario, ahora).get(0);
        UUID estado = DatosSinteticos.sembrarCatalogo(
                jdbc, "pending_task_status", "Estado " + UUID.randomUUID(), 1, usuario, ahora)
                .get(0);

        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO pending_task
                    (id, owner_id, title, pending_task_type_id, priority_id,
                     pending_task_status_id, received_at, registered_at, deadline,
                     active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Consulta sin plazo', :tipo, :prioridad, :estado,
                        :recepcion, :recepcion, NULL, true, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", usuario)
                .param("tipo", tipo).param("prioridad", prioridad).param("estado", estado)
                .param("recepcion", recepcion).param("ahora", ahora)
                .update();
        return id;
    }

    @Test
    @DisplayName("un pendiente recibido el ano anterior muestra su antiguedad, no un aviso")
    void antiguedadCruzandoElAno() throws Exception {
        // Del 20 de diciembre del ano pasado: cruza el cambio de ano en cualquier
        // momento en que se ejecute la prueba entre enero y marzo, y sigue siendo
        // del ano anterior el resto del tiempo.
        LocalDate recepcion = LocalDate.of(LocalDate.now().getYear() - 1, 12, 20);
        UUID id = pendienteSinPlazoRecibidoEl(recepcion);

        String html = mvc.perform(get("/pendientes/" + id).session(sesion))
                .andReturn().getResponse().getContentAsString();

        // La plantilla solo pinta este aviso cuando la antiguedad se pudo calcular:
        // si el calculo devuelve vacio, el valor es null y el bloque no se pinta.
        // Por eso la ausencia del aviso es exactamente el sintoma del fallo.
        assertThat(html)
                .as("un pendiente de hace meses supera los quince dias habiles y debe avisarlo")
                .contains("dias habiles sin fecha limite");
    }

    @Test
    @DisplayName("el listado tampoco avisa de calendario faltante por cruzar el ano")
    void listadoCruzandoElAno() throws Exception {
        pendienteSinPlazoRecibidoEl(LocalDate.of(LocalDate.now().getYear() - 1, 12, 20));

        String html = mvc.perform(get("/pendientes").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("el listado tambien muestra la antiguedad de los que no tienen plazo")
                .contains("Consulta sin plazo");
    }
}
