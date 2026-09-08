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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.team.SemanaDeTrabajo;
import pe.org.beneficencia.legalcontrol.team.TeamWorkloadRepository;

/**
 * La vista de equipo (insumo, seccion 5.2).
 *
 * <p>Dos comprobaciones que parecen menores y no lo son: quien no tiene nada tiene
 * que <b>salir con ceros</b> —es la persona a la que se le puede asignar trabajo, y
 * un {@code JOIN} en vez de {@code LEFT JOIN} la borraria— y la <b>jefa tiene que
 * aparecer</b>, porque lleva expedientes y filtrar por rol la dejaria fuera de la
 * vista que sirve para repartir el trabajo del area.
 */
@AutoConfigureMockMvc
class EquipoIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TeamWorkloadRepository cargas;

    private LocalDate hoy;
    private UUID jefaId;
    private UUID conCarga;
    private UUID sinNada;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        hoy = LocalDate.now();
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        conCarga = SesionDePrueba.crearCuenta(jdbc, encoder, "cargado@ejemplo.test", "LAWYER");
        sinNada = SesionDePrueba.crearCuenta(jdbc, encoder, "libre@ejemplo.test", "LAWYER");
        DatosSinteticos.sembrarCalendario(jdbc, jefaId, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);
    }

    private List<TeamWorkloadRepository.Fila> filas() {
        return cargas.cargaDelEquipo(hoy, SemanaDeTrabajo.lunesDe(hoy),
                SemanaDeTrabajo.domingoDe(hoy), hoy.minusDays(30));
    }

    private TeamWorkloadRepository.Fila de(UUID id) {
        return filas().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("quien no tiene nada sale igualmente, con ceros")
    void sinCargaSaleConCeros() {
        DatosSinteticos.sembrarCargaDesigual(jdbc, List.of(conCarga), hoy);

        var libre = de(sinNada);

        assertThat(libre.activos())
                .as("con JOIN en vez de LEFT JOIN desapareceria, y es a quien se le asigna")
                .isZero();
        assertThat(libre.vencidos()).isZero();
        assertThat(libre.estaSemana()).isZero();
    }

    @Test
    @DisplayName("la jefa aparece en la lista con su carga")
    void laJefaAparece() {
        DatosSinteticos.sembrarCargaDesigual(jdbc, List.of(jefaId), hoy);

        assertThat(filas()).extracting(TeamWorkloadRepository.Fila::id)
                .as("filtrar por role = 'LAWYER' la borraria de su propia vista")
                .contains(jefaId);
        assertThat(de(jefaId).activos()).isPositive();
    }

    @Test
    @DisplayName("una cuenta dada de baja no aparece")
    void losInactivosNoSalen() {
        UUID baja = SesionDePrueba.crearCuenta(jdbc, encoder, "baja@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET status = 'INACTIVE' WHERE id = :id")
                .param("id", baja).update();

        assertThat(filas()).extracting(TeamWorkloadRepository.Fila::id).doesNotContain(baja);
    }

    @Test
    @DisplayName("los cumplidos no cuentan como carga")
    void loCumplidoNoEsCarga() {
        var ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, conCarga, 1, 2);
        assertThat(ids).hasSize(4);

        assertThat(de(conCarga).activos())
                .as("estar hecho deja de pesar")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cualquier usuario puede abrir la vista, no solo la jefatura")
    void laLecturaEsCompartida() throws Exception {
        var sesion = SesionDePrueba.entrar(mvc, "libre@ejemplo.test");

        String html = mvc.perform(get("/equipo").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Carga del equipo", "Responsable", "Vence esta semana");
    }

    @Test
    @DisplayName("la pantalla dice de que semana esta hablando")
    void muestraElRangoDeLaSemana() throws Exception {
        var sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");

        String html = mvc.perform(get("/equipo").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(SemanaDeTrabajo.lunesDe(hoy).toString());
        assertThat(html).contains(SemanaDeTrabajo.domingoDe(hoy).toString());
    }
}
