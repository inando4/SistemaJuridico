package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.List;
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

import pe.org.beneficencia.legalcontrol.team.SemanaDeTrabajo;
import pe.org.beneficencia.legalcontrol.team.TeamWorkloadRepository;

/**
 * Cada numero lleva a su lista, y la lista tiene exactamente esas filas.
 *
 * <p>Es el desajuste que la 004 tuvo que corregir: la tarjeta contaba lo de una
 * persona y el listado mostraba lo de todo el mundo, asi que el numero y la lista
 * no coincidian. Sin {@code responsable} y {@code visibilidad} en el enlace vuelve
 * a pasar.
 */
@AutoConfigureMockMvc
class EquipoEnlacesIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TeamWorkloadRepository cargas;

    private LocalDate hoy;
    private UUID abogadaA;
    private UUID abogadoB;
    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        hoy = LocalDate.now();
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        DatosSinteticos.sembrarCalendario(jdbc, jefa, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);
        // Los dos con carga: si el enlace no filtra, la lista traeria la de ambos.
        DatosSinteticos.sembrarCargaDesigual(jdbc, List.of(abogadaA, abogadoB), hoy);
        sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
    }

    /**
     * Los pendientes que el listado muestra de verdad.
     *
     * <p>Se cuentan por identificador y no por enlaces: cada fila pinta varios
     * (ficha, editar, historial) y contar enlaces daria un multiplo. Buscar los
     * identificadores que la base dice que deberian salir es lo que comprueba el
     * cuadre de verdad.
     */
    private long filasDelListado(String query, List<UUID> esperados) throws Exception {
        String html = mvc.perform(get("/pendientes?" + query).session(sesion))
                .andReturn().getResponse().getContentAsString();
        return esperados.stream().filter(id -> html.contains(id.toString())).count();
    }

    /** Los identificadores que la condicion del recuento selecciona. */
    private List<UUID> segunLaBase(String condicion) {
        return jdbc.sql("""
                SELECT id FROM pending_task
                WHERE active AND completed_at IS NULL AND owner_id = :quien AND (%s)
                """.formatted(condicion))
                .param("quien", abogadaA).param("hoy", hoy)
                .param("lunes", SemanaDeTrabajo.lunesDe(hoy))
                .param("domingo", SemanaDeTrabajo.domingoDe(hoy))
                .query(UUID.class).list();
    }

    private TeamWorkloadRepository.Fila de(UUID id) {
        return cargas.cargaDelEquipo(hoy, SemanaDeTrabajo.lunesDe(hoy),
                        SemanaDeTrabajo.domingoDe(hoy), hoy.minusDays(30)).stream()
                .filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("el enlace de la vista lleva el responsable y la visibilidad")
    void elEnlaceFiltraPorPersona() throws Exception {
        String html = mvc.perform(get("/equipo").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("sin estos dos parametros el listado mostraria lo de todo el mundo")
                .contains("ownerId=" + abogadaA)
                .contains("visibility=all");
        assertThat(html).contains("alerta=vencidos", "alerta=semana", "alerta=sin-plazo-antiguos");
    }

    @Test
    @DisplayName("el listado de vencidos trae exactamente los que decia el numero")
    void vencidosCuadran() throws Exception {
        var esperados = segunLaBase("deadline < :hoy");
        assertThat(esperados).hasSize(de(abogadaA).vencidos());

        long listados = filasDelListado(
                "alerta=vencidos&ownerId=" + abogadaA + "&visibility=all", esperados);

        assertThat(listados).isEqualTo(esperados.size());
    }

    @Test
    @DisplayName("el listado de la semana trae exactamente los que decia el numero")
    void laSemanaCuadra() throws Exception {
        var esperados = segunLaBase(
                "(deadline BETWEEN :lunes AND :domingo) OR"
                        + " (scheduled_for BETWEEN :lunes AND :domingo)");
        assertThat(esperados)
                .as("el foco «semana» tiene que usar la misma condicion que el recuento")
                .hasSize(de(abogadaA).estaSemana());

        long listados = filasDelListado(
                "alerta=semana&ownerId=" + abogadaA + "&visibility=all", esperados);

        assertThat(listados).isEqualTo(esperados.size());
    }

    @Test
    @DisplayName("el listado NO trae los de la otra persona")
    void noSeCuelaLoAjeno() throws Exception {
        var deB = jdbc.sql("""
                SELECT id FROM pending_task WHERE owner_id = :b AND deadline < :hoy
                """).param("b", abogadoB).param("hoy", hoy).query(UUID.class).list();
        assertThat(deB).isNotEmpty();

        long colados = filasDelListado(
                "alerta=vencidos&ownerId=" + abogadaA + "&visibility=all", deB);

        assertThat(colados)
                .as("es el desajuste que la 004 tuvo que corregir en sus tarjetas")
                .isZero();
    }
}
