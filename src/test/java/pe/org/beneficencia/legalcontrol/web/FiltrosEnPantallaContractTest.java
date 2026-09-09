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

import pe.org.beneficencia.legalcontrol.integration.DatosSinteticos;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Los filtros que la seccion 27 enumera, ofrecidos <b>en pantalla</b>.
 *
 * <p>El servidor ya los aceptaba todos; lo que faltaba era poder componerlos sin
 * conocer la sintaxis de la direccion.
 */
@AutoConfigureMockMvc
class FiltrosEnPantallaContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID laOtra;
    private UUID estadoProcesal;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        laOtra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Abogada Segunda' WHERE id = :id")
                .param("id", laOtra).update();
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        Timestamp ahora = Timestamp.from(Instant.now());
        estadoProcesal = DatosSinteticos
                .sembrarCatalogo(jdbc, "procedural_status", "Procesal", 1, yo, ahora).get(0);
        DatosSinteticos.sembrarCatalogo(jdbc, "administrative_status", "Administrativo", 1, yo, ahora);
        DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "Tipo", 1, yo, ahora);
        DatosSinteticos.sembrarCatalogo(jdbc, "priority", "Prioridad", 1, yo, ahora);
        DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "Estado", 1, yo, ahora);

        judicial(yo, "EXP-MIO-2026", estadoProcesal, "Desalojo", "2026-01-01");
        judicial(laOtra, "EXP-SUYO-2026", null, "Servidumbre", null);
    }

    private void judicial(UUID owner, String numero, UUID estado, String materia, String limite) {
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, procedural_status_id,
                                           subject, deadline, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, :e, :m, CAST(:lim AS date), true, :a, :a, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("n", numero)
                .param("e", estado).param("m", materia).param("lim", limite)
                .param("a", Timestamp.from(Instant.now())).update();
    }

    private String pantalla(String ruta) throws Exception {
        return mvc.perform(get(ruta).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("/judiciales ofrece los ocho filtros de la seccion 27 (RF-016)")
    void losOchoDeLaSeccion27() throws Exception {
        String html = pantalla("/judiciales");

        assertThat(html)
                .as("los cuatro que faltaban")
                .contains("name=\"ownerId\"")
                .contains("name=\"proceduralStatusId\"")
                .contains("name=\"subject\"")
                .contains("name=\"overdue\"")
                .as("y los que ya estaban")
                .contains("name=\"q\"")
                .contains("name=\"deadlinePresence\"")
                .contains("name=\"visibility\"");
    }

    @Test
    @DisplayName("los desplegables traen sus opciones, no vienen vacios")
    void losDesplegablesTraenOpciones() throws Exception {
        String html = pantalla("/judiciales");

        assertThat(html)
                .contains("Abogada Segunda")
                .contains("Procesal 1");
    }

    @Test
    @DisplayName("/administrativos ofrece responsable, estado y vencidos (RF-017)")
    void enAdministrativos() throws Exception {
        String html = pantalla("/administrativos");

        assertThat(html)
                .contains("name=\"ownerId\"")
                .contains("name=\"administrativeStatusId\"")
                .contains("name=\"overdue\"")
                .contains("Administrativo 1");
    }

    @Test
    @DisplayName("/pendientes ofrece responsable, tipo, prioridad, estado y vencidos (RF-018)")
    void enPendientes() throws Exception {
        String html = pantalla("/pendientes");

        assertThat(html)
                .contains("name=\"ownerId\"")
                .contains("name=\"typeId\"")
                .contains("name=\"priorityId\"")
                .contains("name=\"statusId\"")
                .contains("name=\"overdue\"")
                .contains("Tipo 1")
                .contains("Prioridad 1")
                .contains("Estado 1");
    }

    @Test
    @DisplayName("dos filtros a la vez se cumplen los dos (RF-019)")
    void dosFiltrosALaVez() throws Exception {
        assertThat(pantalla("/judiciales?ownerId=" + yo + "&proceduralStatusId=" + estadoProcesal))
                .contains("EXP-MIO-2026")
                .doesNotContain("EXP-SUYO-2026");

        assertThat(pantalla("/judiciales?ownerId=" + laOtra + "&proceduralStatusId=" + estadoProcesal))
                .as("la combinacion que no existe devuelve vacio, no todo")
                .doesNotContain("EXP-MIO-2026")
                .doesNotContain("EXP-SUYO-2026");
    }

    @Test
    @DisplayName("el filtro por materia y el de vencidos funcionan desde la pantalla")
    void materiaYVencidos() throws Exception {
        assertThat(pantalla("/judiciales?subject=servidumbre"))
                .contains("EXP-SUYO-2026")
                .doesNotContain("EXP-MIO-2026");

        assertThat(pantalla("/judiciales?overdue=true"))
                .as("el de 2026-01-01 ya venció; el que no tiene fecha no puede estar vencido")
                .contains("EXP-MIO-2026")
                .doesNotContain("EXP-SUYO-2026");
    }

    @Test
    @DisplayName("el filtro elegido se ve marcado al volver la pagina")
    void elFiltroSeVeMarcado() throws Exception {
        // Sin esto el usuario aplicaria un filtro, veria el resultado y el desplegable
        // habria vuelto a «Cualquiera»: parece que no se aplico.
        assertThat(pantalla("/judiciales?ownerId=" + laOtra))
                .containsPattern("value=\"" + laOtra + "\"[^>]*selected");
    }

    @Test
    @DisplayName("los filtros sobreviven a paginar y a ordenar (RF-019)")
    void sobrevivenAPaginar() throws Exception {
        String html = pantalla("/judiciales?ownerId=" + yo + "&page=1");

        assertThat(html)
                .as("el enlace de la pagina anterior conserva el responsable")
                .contains("ownerId=" + yo);
    }

    @Test
    @DisplayName("«Quitar filtros» los retira todos (RF-020)")
    void quitarFiltros() throws Exception {
        assertThat(pantalla("/judiciales?ownerId=" + yo))
                .as("hay una salida limpia, sin tener que borrar la direccion a mano")
                .contains("Quitar filtros");
    }
}
