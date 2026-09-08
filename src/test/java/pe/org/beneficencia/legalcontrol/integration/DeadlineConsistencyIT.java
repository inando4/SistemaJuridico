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
 * Listado y ficha deben decir exactamente lo mismo del mismo plazo.
 *
 * <p>Si discreparan, la gente dejaria de fiarse de los dos. Y el aviso por falta
 * de calendario tiene que aparecer en vez de un numero: un conteo inventado
 * parece fiable, y eso es peor que no dar ninguno.
 */
@AutoConfigureMockMvc
class DeadlineConsistencyIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID expediente;
    private UUID usuario;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        jdbc.sql("DELETE FROM calendar_review").update();
        jdbc.sql("DELETE FROM non_working_day").update();
        jdbc.sql("DELETE FROM calendar_year").update();

        usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");

        expediente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, deadline,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-PLAZO-2026', :limite, true, :ahora, :ahora, 1)
                """)
                .param("id", expediente).param("owner", usuario)
                .param("limite", LocalDate.now().plusDays(30))
                .param("ahora", ahora).update();
    }

    private String listado() throws Exception {
        return mvc.perform(get("/judiciales").session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    private String ficha() throws Exception {
        return mvc.perform(get("/judiciales/" + expediente).session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("sin calendario revisado, ambas pantallas avisan en vez de contar")
    void sinCalendarioAmbasAvisan() throws Exception {
        assertThat(listado()).contains("Cálculo no disponible");
        assertThat(ficha()).contains("Cálculo no disponible");
    }

    @Test
    @DisplayName("con el calendario revisado, ambas muestran el mismo conteo")
    void conCalendarioAmbasCuentanIgual() throws Exception {
        prepararCalendarioRevisado();

        String enListado = extraerConteo(listado());
        String enFicha = extraerConteo(ficha());

        assertThat(enListado).isNotBlank();
        assertThat(enFicha).as("listado y ficha no pueden discrepar").isEqualTo(enListado);
    }

    /** Deja el ano en curso y el siguiente con dias cargados y revision confirmada. */
    private void prepararCalendarioRevisado() {
        Timestamp ahora = Timestamp.from(Instant.now());
        for (int ano : new int[]{LocalDate.now().getYear(), LocalDate.now().plusDays(400).getYear()}) {
            jdbc.sql("""
                    INSERT INTO calendar_year (year, revision, created_by, created_at)
                    VALUES (:ano, 1, :usuario, :ahora)
                    ON CONFLICT (year) DO NOTHING
                    """).param("ano", ano).param("usuario", usuario).param("ahora", ahora).update();

            jdbc.sql("""
                    INSERT INTO non_working_day (id, day, description, kind, created_by,
                                                 created_at, updated_at, version)
                    VALUES (:id, :dia, 'Feriado de prueba', 'NATIONAL_HOLIDAY', :usuario,
                            :ahora, :ahora, 1)
                    ON CONFLICT (day) DO NOTHING
                    """).param("id", UUID.randomUUID())
                    .param("dia", LocalDate.of(ano, 1, 1))
                    .param("usuario", usuario).param("ahora", ahora).update();

            jdbc.sql("""
                    INSERT INTO calendar_review (id, year, reviewed_revision, reviewed_by,
                                                 reviewed_at, full_year_reviewed)
                    VALUES (:id, :ano, 1, :usuario, :ahora, true)
                    ON CONFLICT (year, reviewed_revision) DO NOTHING
                    """).param("id", UUID.randomUUID()).param("ano", ano)
                    .param("usuario", usuario).param("ahora", ahora).update();
        }
    }

    private String extraerConteo(String html) {
        var m = java.util.regex.Pattern.compile("(\\d+) días hábiles restantes").matcher(html);
        return m.find() ? m.group(1) : "";
    }
}
