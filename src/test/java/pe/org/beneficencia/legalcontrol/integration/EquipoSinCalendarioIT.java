package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
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

/**
 * Sin calendario se degrada <b>una</b> columna, no la pantalla.
 *
 * <p>Es la distincion que el sistema ya hace y que {@code AlertLevelsIT} fijo:
 * comparar {@code deadline < hoy} o contra un lunes es comparar fechas, y no
 * interviene ningun dia habil. Solo «lleva mas de quince dias habiles esperando»
 * necesita el calendario.
 *
 * <p>Ocultar la vista entera dejaria a la jefatura sin poder repartir trabajo por
 * un dato que solo afecta a una de las cuatro columnas.
 */
@AutoConfigureMockMvc
class EquipoSinCalendarioIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private LocalDate hoy;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        hoy = LocalDate.now();
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        UUID abogada = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        DatosSinteticos.sembrarCalendario(jdbc, jefa, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);

        var ahora = java.sql.Timestamp.from(java.time.Instant.now());
        UUID tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "T", 1,
                abogada, ahora).get(0);
        UUID prio = DatosSinteticos.sembrarCatalogo(jdbc, "priority", "P", 1,
                abogada, ahora).get(0);
        UUID est = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "E", 1,
                abogada, ahora).get(0);

        DatosSinteticos.pendienteSuelto(jdbc, abogada, tipo, prio, est, "Vencido",
                hoy.minusDays(5), hoy.minusDays(20));
        DatosSinteticos.pendienteSuelto(jdbc, abogada, tipo, prio, est, "De esta semana",
                SemanaDeTrabajo.lunesDe(hoy), hoy.minusDays(2));
        DatosSinteticos.pendienteSuelto(jdbc, abogada, tipo, prio, est, "Sin plazo antiguo",
                null, hoy.minusDays(90));
    }

    private String pagina() throws Exception {
        var sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
        return mvc.perform(get("/equipo").session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("con el ano revisado se ven las cuatro columnas con numeros")
    void conCalendarioTodoCuenta() throws Exception {
        String html = pagina();

        assertThat(html).doesNotContain("Faltan días no laborables por revisar");
        assertThat(html).contains("Sin plazo, antiguos");
    }

    @Test
    @DisplayName("sin el ano revisado solo se degrada la columna que depende de el")
    void sinCalendarioSoloUnaColumna() throws Exception {
        jdbc.sql("DELETE FROM calendar_review").update();

        String html = pagina();

        assertThat(html)
                .as("el aviso sustituye al numero que no se puede calcular")
                .contains("Faltan días no laborables por revisar");
        assertThat(html)
                .as("la pantalla sigue sirviendo: repartir trabajo no depende de esa columna")
                .contains("Carga del equipo", "Vencidos", "Vence esta semana");
    }

    @Test
    @DisplayName("sin calendario, los recuentos que comparan fechas siguen siendo correctos")
    void losQueNoDependenSiguenContando() throws Exception {
        jdbc.sql("DELETE FROM calendar_review").update();

        String html = pagina();

        // El vencido y el de la semana siguen contandose: un cero ahi seria falso.
        int filaAbogada = html.indexOf("Usuario a@ejemplo.test");
        assertThat(filaAbogada).isPositive();
        assertThat(html.substring(filaAbogada, Math.min(filaAbogada + 900, html.length())))
                .as("dos cosas encima: una vencida y una de esta semana")
                .contains(">2<");
    }
}
