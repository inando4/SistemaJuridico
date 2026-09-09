package pe.org.beneficencia.legalcontrol.integration;

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

/**
 * El buscador global encuentra por los campos que el insumo enumera, y su relacion
 * con los listados es exactamente la que la especificacion afirma.
 *
 * <p>La parte que importa es la ultima: el buscador y el listado <b>coinciden en los
 * activos</b> y <b>difieren en los archivados</b>, a proposito. Con datos de prueba
 * todos activos, una implementacion que reutilizara la consulta entera del listado
 * —heredando su {@code visibility=active}— pasaria inadvertida.
 */
@AutoConfigureMockMvc
class BusquedaGlobalIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private static final String TERMINO = "servidumbre";

    private MockHttpSession sesion;
    private UUID abogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        // Cada registro lleva el termino en UN solo campo, y nunca en el numero ni
        // en los nombres: asi cada aparicion prueba una columna concreta.
        judicial("EXP-MAT-2026", "Servidumbre de paso", null, true);
        judicial("EXP-OBS-2026", "Desalojo", "Coordinado por servidumbre vecinal", true);
        judicial("EXP-ARC-2026", "Servidumbre antigua", null, false);
        administrativo("ADM-OBS-2026", "Consulta sobre servidumbre");
        pendiente("Revisar convenio", "Depende de la servidumbre del predio");
    }

    private void judicial(String numero, String materia, String notas, boolean activo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject, notes,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :materia, :notas, :activo, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("numero", numero).param("materia", materia).param("notas", notas)
                .param("activo", activo).param("ahora", ahora).update();
    }

    private void administrativo(String numero, String notas) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, requesting_area,
                                                      notes, active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, 'Contabilidad', :notas, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("numero", numero).param("notas", notas).param("ahora", ahora).update();
    }

    private void pendiente(String titulo, String notas) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, notes, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :notas, :hoy, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("titulo", titulo).param("notas", notas)
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private String pantalla(String ruta) throws Exception {
        return mvc.perform(get(ruta).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("encuentra por materia y por observaciones en los tres tipos")
    void encuentraPorLosCamposDelInsumo() throws Exception {
        String html = pantalla("/buscar?q=" + TERMINO);

        assertThat(html)
                .as("judicial que solo coincide por materia")
                .contains("EXP-MAT-2026");
        assertThat(html)
                .as("judicial que solo coincide por observaciones")
                .contains("EXP-OBS-2026");
        assertThat(html)
                .as("administrativo que solo coincide por observaciones")
                .contains("ADM-OBS-2026");
        assertThat(html)
                .as("pendiente que solo coincide por observaciones")
                .contains("Revisar convenio");
    }

    @Test
    @DisplayName("el buscador alcanza los archivados y los señala")
    void alcanzaLosArchivados() throws Exception {
        String html = pantalla("/buscar?q=" + TERMINO);

        assertThat(html)
                .as("el caso por el que preguntan puede estar cerrado")
                .contains("EXP-ARC-2026").contains("archivado");
    }

    @Test
    @DisplayName("buscador y listado coinciden en los activos y difieren en los archivados")
    void coincidenEnLosActivos() throws Exception {
        String buscador = pantalla("/buscar?q=" + TERMINO);
        String listado = pantalla("/judiciales?q=" + TERMINO);
        String listadoTodos = pantalla("/judiciales?q=" + TERMINO + "&visibility=all");

        // Coinciden: es lo que se rompe si se amplia la condicion en un sitio y no
        // en el otro, y solo se ve con un registro que coincida por materia.
        assertThat(listado).contains("EXP-MAT-2026").contains("EXP-OBS-2026");
        assertThat(buscador).contains("EXP-MAT-2026").contains("EXP-OBS-2026");

        // Difieren, y esa diferencia es de visibilidad, no de campos.
        assertThat(listado)
                .as("el listado es una lista de trabajo: por omision, solo lo activo")
                .doesNotContain("EXP-ARC-2026");
        assertThat(listadoTodos)
                .as("pedido explicitamente, el listado tambien lo trae")
                .contains("EXP-ARC-2026");
    }
}
