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
 * Contrato del filtro por un expediente <b>concreto</b>.
 *
 * <p>Distinto del filtro por clase de vinculo que ya cubria
 * {@link PendingTaskListContractTest}: aquel dice «de algun judicial», este dice
 * «de este».
 */
@AutoConfigureMockMvc
class PendingTaskFilterContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID expedienteA;
    private UUID expedienteB;
    private UUID procedimiento;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        expedienteA = judicial(abogado, "EXP-FILTRO-A");
        expedienteB = judicial(abogado, "EXP-FILTRO-B");
        procedimiento = administrativo(abogado, "ADM-FILTRO-1");

        pendiente(abogado, "Escrito del expediente A", expedienteA, null);
        pendiente(abogado, "Alegato del expediente A", expedienteA, null);
        pendiente(abogado, "Recurso del expediente B", expedienteB, null);
        pendiente(abogado, "Informe del procedimiento", null, procedimiento);
        pendiente(abogado, "Comprar toner", null, null);
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

    private void pendiente(UUID owner, String titulo, UUID j, UUID a) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                          administrative_procedure_id, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :a, :hoy, true, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("j", j).param("a", a).param("hoy", LocalDate.now())
                .param("ts", ahora).update();
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

    private String listado(String query) throws Exception {
        return mvc.perform(get("/pendientes" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("filtra por un expediente judicial concreto, no por la clase (RF-008)")
    void porExpedienteJudicialConcreto() throws Exception {
        String html = listado("?judicialCaseId=" + expedienteA);

        assertThat(html)
                .contains("Escrito del expediente A")
                .contains("Alegato del expediente A")
                .as("el del otro expediente judicial no es de este")
                .doesNotContain("Recurso del expediente B")
                .doesNotContain("Informe del procedimiento")
                .doesNotContain("Comprar toner");
    }

    @Test
    @DisplayName("filtra por un procedimiento administrativo concreto (RF-008)")
    void porProcedimientoConcreto() throws Exception {
        String html = listado("?administrativeProcedureId=" + procedimiento);

        assertThat(html)
                .contains("Informe del procedimiento")
                .doesNotContain("Escrito del expediente A")
                .doesNotContain("Comprar toner");
    }

    @Test
    @DisplayName("los dos identificadores a la vez se rechazan")
    void losDosALaVezSeRechazan() throws Exception {
        // Ningun pendiente cuelga de un judicial y de un administrativo (insumo,
        // seccion 9), asi que la combinacion devolveria vacio siempre. Un vacio
        // silencioso haria pensar que no hay trabajo; el rechazo dice que la
        // pregunta no tiene sentido.
        mvc.perform(get("/pendientes")
                        .param("judicialCaseId", expedienteA.toString())
                        .param("administrativeProcedureId", procedimiento.toString())
                        .session(sesion))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("un expediente inexistente da lista vacia con aviso, no un error (RF-011)")
    void expedienteInexistente() throws Exception {
        String html = listado("?judicialCaseId=00000000-0000-0000-0000-000000000000");

        assertThat(html)
                .as("la respuesta es 200: lo comprueba el propio ayudante listado()")
                .doesNotContain("Escrito del expediente A")
                .doesNotContain("Comprar toner");
    }

    @Test
    @DisplayName("un identificador mal formado no revienta la pantalla")
    void identificadorMalFormado() throws Exception {
        mvc.perform(get("/pendientes?judicialCaseId=no-es-un-uuid").session(sesion))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("el filtro sobrevive a paginar y a ordenar (RF-009)")
    void sobreviveAPaginarYOrdenar() throws Exception {
        // Los enlaces de paginacion los construye comoQuery(), que es uno de los dos
        // portadores del filtro.
        String html = listado("?judicialCaseId=" + expedienteA + "&sort=title");

        assertThat(html)
                .contains("Alegato del expediente A")
                .doesNotContain("Recurso del expediente B");

        assertThat(listado("?judicialCaseId=" + expedienteA + "&page=1"))
                .as("una pagina fuera de rango no pierde el filtro ni da error")
                .doesNotContain("Recurso del expediente B");
    }

    @Test
    @DisplayName("el filtro viaja en los enlaces de paginacion")
    void elFiltroViajaEnLosEnlaces() throws Exception {
        String html = listado("?judicialCaseId=" + expedienteA + "&page=1");

        // La pagina 1 esta vacia, asi que hay enlace a la anterior: tiene que
        // llevar el expediente. Sin el anadir() de comoQuery, este enlace volveria
        // al listado completo y el usuario perderia el contexto sin avisar.
        assertThat(html)
                .as("el enlace de la pagina anterior conserva el expediente")
                .contains("judicialCaseId=" + expedienteA);
    }

    @Test
    @DisplayName("el formulario de filtros lleva el expediente en un campo oculto (RF-009)")
    void elFormularioLlevaCampoOculto() throws Exception {
        // El otro portador. Un <form method="get"> descarta todo lo que no sean sus
        // propios campos: sin el oculto, pulsar «Aplicar filtros» borraria el
        // expediente y el usuario veria de golpe los pendientes de todo el mundo.
        String html = listado("?judicialCaseId=" + expedienteA);

        assertThat(html)
                .contains("type=\"hidden\"")
                .contains("name=\"judicialCaseId\"");
    }

    @Test
    @DisplayName("el filtro sobrevive a aplicar OTRO filtro desde el formulario (RF-009)")
    void sobreviveAAplicarOtroFiltro() throws Exception {
        // Este es el caso que se rompe si falta el campo oculto: el usuario cambia
        // «Registros» a «Todos», pulsa «Aplicar filtros», y el formulario GET envia
        // solo sus campos. Sin el oculto, el expediente desaparece y de golpe
        // aparecen los pendientes de todo el mundo.
        String html = listado("?judicialCaseId=" + expedienteA + "&visibility=all");

        assertThat(html)
                .contains("Escrito del expediente A")
                .as("el otro expediente sigue fuera pese al cambio de visibilidad")
                .doesNotContain("Recurso del expediente B")
                .doesNotContain("Comprar toner");
    }

    @Test
    @DisplayName("con el filtro puesto se nombra el expediente y se ofrece quitarlo (RF-010)")
    void nombraElExpedienteFiltrado() throws Exception {
        String html = listado("?judicialCaseId=" + expedienteA);

        // Afirmar solo que «EXP-FILTRO-A» aparece en alguna parte no vale: el numero
        // ya sale en la columna «Expediente» de cada fila, asi que la prueba pasaria
        // sin que existiera ningun aviso. Hay que exigir el texto del aviso.
        assertThat(avisoDelExpediente(html))
                .as("un UUID en pantalla no le dice nada a nadie: hace falta el numero")
                .contains("Pendientes del expediente")
                .contains("EXP-FILTRO-A")
                .as("y una salida, para no dejar al usuario atrapado en el filtro")
                .contains("Ver todos los pendientes");
    }
}
