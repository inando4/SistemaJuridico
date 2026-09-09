package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Presupuesto de consultas de las pantallas de pendientes.
 *
 * <p>El listado hace seis joins —responsable, tres catalogos y los dos expedientes
 * posibles— pero en <b>una sola consulta</b>. Y el conteo de reprogramaciones de
 * «cumplidas» es una agregacion para toda la pagina, no una por fila.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class PendingTaskQueryBudgetIT extends PostgresIntegrationTest {

    private static final int PENDIENTES = 300;
    private static final int FILAS_POR_PAGINA = 25;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID alguno;

    @BeforeEach
    void preparar() throws Exception {
        if (sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
            int ano = java.time.LocalDate.now().getYear();
            DatosSinteticos.sembrarCalendario(jdbc, usuario, ano, ano + 1, ano + 2);
            DatosSinteticos.sembrarPendientes(jdbc, usuario, PENDIENTES, 10);
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        alguno = jdbc.sql("SELECT id FROM pending_task LIMIT 1").query(UUID.class).single();
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        return total == null || total < PENDIENTES;
    }

    private long costeDe(String ruta) {
        return ContadorDeConsultas.contar(() -> {
            try {
                mvc.perform(get(ruta).session(sesion));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("el listado filtrado por expediente cuesta una consulta mas, y solo entonces")
    void listadoFiltradoPorExpediente() {
        UUID expediente = UUID.randomUUID();
        // Mismo motivo que en PendingTaskPerformanceTest: el responsable sale de un
        // pendiente existente, no de un correo que puede no estar en esta base.
        UUID responsable = jdbc.sql("SELECT owner_id FROM pending_task LIMIT 1")
                .query(UUID.class).single();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-PRESUPUESTO-2026', true, now(), now(), 1)
                """).param("id", expediente).param("o", responsable).update();
        jdbc.sql("""
                UPDATE pending_task SET judicial_case_id = :j
                WHERE id IN (SELECT id FROM pending_task
                             -- Solo los que no cuelgan ya de un administrativo: la
                             -- V9 impide que un pendiente tenga los dos vinculos.
                             WHERE administrative_procedure_id IS NULL LIMIT 40)
                """).param("j", expediente).update();

        long sinFiltro = costeDe("/pendientes");
        long conFiltro = costeDe("/pendientes?judicialCaseId=" + expediente);
        System.out.printf("Listado: %d consultas sin filtro, %d con expediente%n",
                sinFiltro, conFiltro);

        // La de mas es la del numero del expediente: los filtros solo llevan el UUID
        // y RF-010 exige nombrarlo. Se paga solo en las peticiones filtradas.
        assertThat(conFiltro)
                .as("una sola consulta mas, no una por fila")
                .isEqualTo(sinFiltro + 1);
    }

    @Test
    @DisplayName("el listado no hace una consulta por pendiente")
    void listadoSinNMasUno() {
        long coste = costeDe("/pendientes");
        System.out.printf("Listado de pendientes: %d consultas%n", coste);

        assertThat(coste)
                .as("seis joins pero una sola consulta; por fila serian %d", FILAS_POR_PAGINA)
                .isLessThanOrEqualTo(10L);
    }


    @Test
    @DisplayName("los desplegables de filtro no cuestan una consulta cada uno (D3, CE-006)")
    void losFiltrosNoCuestanUnaConsultaCadaUno() {
        long coste = costeDe("/pendientes");
        System.out.printf("Listado de pendientes con filtros: %d consultas%n", coste);

        // El techo del plan. Una consulta por desplegable habria llevado esta pantalla
        // a 10; el UNION de CatalogRepository las junta en una.
        assertThat(coste)
                .as("dos consultas para las opciones: catalogos y cuentas")
                .isLessThanOrEqualTo(8L);
    }

    @Test
    @DisplayName("las acciones por fila no cuestan una consulta por fila (CE-006)")
    void lasAccionesNoEscalanConLasFilas() {
        // puedeActuar compara dos identificadores en memoria y SELECCION ya trae el
        // responsable de cada fila. Si alguien resolviera el permiso consultando, el
        // coste subiria con el numero de filas y esta comparacion lo delataria.
        long conVeinticinco = costeDe("/pendientes");
        long conUna = costeDe("/pendientes?q=" + java.util.UUID.randomUUID());
        System.out.printf("Listado con acciones: %d consultas con 25 filas, %d con 0%n",
                conVeinticinco, conUna);

        assertThat(conVeinticinco)
                .as("el mismo numero con la pagina llena que con la pagina vacia")
                .isEqualTo(conUna);
    }

    @Test
    @DisplayName("la pantalla de hoy tampoco escala con las filas")
    void hoyAcotada() {
        long coste = costeDe("/pendientes/hoy");
        System.out.printf("Pendientes de hoy: %d consultas%n", coste);
        assertThat(coste).isLessThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("cumplidas cuenta las reprogramaciones en una sola agregacion")
    void cumplidasConUnaAgregacion() {
        long coste = costeDe("/cumplidos");
        System.out.printf("Tareas cumplidas: %d consultas%n", coste);

        // Una consulta por fila para contar reprogramaciones daria 25 mas.
        assertThat(coste).isLessThanOrEqualTo(12L);
    }

    @Test
    @DisplayName("la ficha se resuelve con un punado de consultas")
    void fichaAcotada() {
        assertThat(costeDe("/pendientes/" + alguno)).isLessThanOrEqualTo(12L);
    }

    @Test
    @DisplayName("el filtrado y el orden no anaden consultas")
    void filtradoAcotado() {
        assertThat(costeDe("/pendientes?q=sintetico&sort=deadline&linkedTo=judicial&visibility=all"))
                .isLessThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("consultar no escribe historial")
    void consultarNoEscribe() {
        Integer antes = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();

        costeDe("/pendientes");
        costeDe("/pendientes/hoy");
        costeDe("/cumplidos");
        costeDe("/pendientes/" + alguno);
        costeDe("/pendientes/" + alguno + "/historial");

        Integer despues = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(despues).isEqualTo(antes);
    }
}
