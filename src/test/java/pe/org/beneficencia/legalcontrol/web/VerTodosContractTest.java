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
 * El acotado de la ficha y su salida al listado filtrado.
 *
 * <p>Lo que se protege aqui es CE-007: que nada vinculado a un expediente quede
 * inalcanzable desde ese expediente por no caber en la ficha.
 */
@AutoConfigureMockMvc
class VerTodosContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID conMuchos;
    private UUID conPocos;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        conMuchos = judicial("EXP-MUCHOS-2026");
        conPocos = judicial("EXP-POCOS-2026");
        vincular(conMuchos, 26);
        vincular(conPocos, 3);
    }

    private UUID judicial(String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", yo).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private void vincular(UUID expediente, int cuantos) {
        Timestamp ahora = Timestamp.from(Instant.now());
        for (int i = 0; i < cuantos; i++) {
            jdbc.sql("""
                    INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                              registered_at, deadline, active,
                                              created_at, updated_at, version)
                    VALUES (:id, :o, :t, :j, :hoy, CAST(:lim AS date), true, :ts, :ts, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("o", yo)
                    .param("t", "Pendiente numero " + i).param("j", expediente)
                    .param("hoy", LocalDate.now())
                    .param("lim", LocalDate.now().plusDays(i + 1).toString())
                    .param("ts", ahora).update();
        }
    }


    /**
     * El texto del aviso del expediente filtrado, o cadena vacia si no hay aviso.
     *
     * <p>Se acota al bloque en vez de buscar el numero en toda la pagina: el numero
     * ya sale en la columna «Expediente» de cada fila, asi que una comprobacion
     * sobre el html entero pasaria aunque el aviso no existiera.
     */
    private String avisoDelExpediente(String html) {
        int desde = html.indexOf("class=\"aviso\"");
        if (desde < 0) {
            return "";
        }
        return html.substring(desde, html.indexOf("</p>", desde));
    }

    private String pagina(String ruta) throws Exception {
        return mvc.perform(get(ruta).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("con 26 vinculos la ficha avisa de que hay mas (RF-006)")
    void avisaCuandoHayMas() throws Exception {
        String html = pagina("/judiciales/" + conMuchos);

        assertThat(html).contains("Hay más pendientes de los que caben aquí");
        assertThat(html).contains("Ver todos");
    }

    @Test
    @DisplayName("con 3 vinculos no avisa: el aviso que sale siempre deja de leerse")
    void noAvisaCuandoCaben() throws Exception {
        String html = pagina("/judiciales/" + conPocos);

        assertThat(html).doesNotContain("Hay más pendientes de los que caben aquí");
        assertThat(html)
                .as("pero el enlace al listado filtrado sigue estando: es la unica via "
                        + "para llegar a los archivados de este expediente")
                .contains("judicialCaseId=" + conPocos);
    }

    @Test
    @DisplayName("el aviso sale del sondeo, sin contar el total")
    void elAvisoNoCuentaElTotal() throws Exception {
        // El principio V prohibe persistir derivados y el IV mirar el coste: la fila
        // de mas que ya pide Paging responde a «hay siguiente» sin un count(*).
        String html = pagina("/judiciales/" + conMuchos);

        int filas = html.split("Pendiente numero ", -1).length - 1;
        assertThat(filas)
                .as("25 por pagina, ni una mas: la fila 26 solo sirvio para saber que existia")
                .isEqualTo(25);
    }

    @Test
    @DisplayName("el enlace «Ver todos» lleva al listado con este expediente filtrado")
    void elEnlaceLlevaAlListadoFiltrado() throws Exception {
        String ficha = pagina("/judiciales/" + conMuchos);

        assertThat(ficha)
                .contains("judicialCaseId=" + conMuchos)
                .contains("visibility=notArchived")
                .contains("sort=pendingFirst");
    }

    @Test
    @DisplayName("el listado filtrado nombra el expediente y ofrece la salida (RF-010)")
    void elListadoNombraElExpediente() throws Exception {
        String html = pagina("/pendientes?judicialCaseId=" + conMuchos);

        assertThat(avisoDelExpediente(html))
                .contains("Pendientes del expediente")
                .contains("EXP-MUCHOS-2026")
                .as("sin salida, quien llega desde una ficha se queda dentro del filtro")
                .contains("Ver todos los pendientes");
    }

    @Test
    @DisplayName("sin filtro no aparece el aviso del expediente")
    void sinFiltroSinAviso() throws Exception {
        assertThat(avisoDelExpediente(pagina("/pendientes")))
                .as("sin filtro no hay bloque de aviso en absoluto")
                .isEmpty();
    }
}
