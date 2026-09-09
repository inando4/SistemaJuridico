package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * Presupuesto del calendario (principio IV).
 *
 * <p><b>La invariante es la razon de ser de esta prueba</b>: el mes tiene que costar
 * las mismas consultas que el dia. Una implementacion que pregunte dia a dia da 31
 * consultas para un mes y 1 para un dia, funciona igual de bien con datos de prueba, y
 * solo duele cuando el area lleva tres años de datos y la base esta en otra region.
 *
 * <p>Y una medicion de <b>tiempo</b>, porque el calendario no pagina: el numero de
 * consultas es 1 pase lo que pase, asi que la invariante no vigila el volumen de filas.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class AgendaQueryBudgetIT extends PostgresIntegrationTest {

    /** Un mes cargado a proposito: varios eventos por dia. */
    private static final LocalDate ANCLA = LocalDate.of(2026, 3, 15);
    private static final int POR_DIA = 6;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        if (sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test",
                    "LAWYER");
            DatosSinteticos.sembrarCalendario(jdbc, abogado, 2026);

            // Marzo entero, con programacion y vencimiento el mismo dia: cada pendiente
            // produce dos eventos, asi que el mes ronda los 370.
            for (int dia = 1; dia <= 31; dia++) {
                for (int n = 0; n < POR_DIA; n++) {
                    pendiente(abogado, "Evento %02d-%02d".formatted(dia, n),
                            LocalDate.of(2026, 3, dia));
                }
            }
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        return total == null || total < 31 * POR_DIA;
    }

    private void pendiente(UUID owner, String titulo, LocalDate dia) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          deadline, active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :registro, :dia, :dia, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", owner).param("titulo", titulo)
                .param("registro", LocalDate.of(2026, 3, 1)).param("dia", dia)
                .param("ahora", ahora).update();
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

    private Duration tiempoDe(String ruta) {
        Instant antes = Instant.now();
        try {
            mvc.perform(get(ruta).session(sesion));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Duration.between(antes, Instant.now());
    }

    @Test
    @DisplayName("el mes cuesta las mismas consultas que el dia")
    void invarianteEntreVistas() {
        long dia = costeDe("/calendario?vista=dia&ancla=" + ANCLA);
        long semana = costeDe("/calendario?vista=semana&ancla=" + ANCLA);
        long mes = costeDe("/calendario?vista=mes&ancla=" + ANCLA);

        System.out.printf("Calendario: %d consultas en dia, %d en semana, %d en mes%n",
                dia, semana, mes);

        assertThat(mes)
                .as("una consulta por rango, no una por dia: 31 dias no pueden ser 31 viajes")
                .isEqualTo(dia);
        assertThat(semana).isEqualTo(dia);
    }

    @Test
    @DisplayName("el calendario se mantiene dentro de su techo de consultas")
    void dentroDelTecho() {
        long coste = costeDe("/calendario?vista=mes&ancla=" + ANCLA);
        System.out.printf("Calendario: %d consultas%n", coste);

        assertThat(coste)
                .as("una de eventos, una de dias no laborables, mas sesion y personas")
                .isLessThanOrEqualTo(7L);
    }

    @Test
    @DisplayName("un mes cargado se pinta dentro del presupuesto de tiempo")
    void tiempoDeUnMesCargado() {
        // Esta es la medicion que importa aqui: como el calendario no pagina, el
        // numero de consultas no dice nada del volumen de filas. Un mes con seis
        // pendientes por dia y dos fechas cada uno son unos 370 eventos.
        List<Long> medidas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            medidas.add(tiempoDe("/calendario?vista=mes&ancla=" + ANCLA).toMillis());
        }
        medidas.sort(Long::compareTo);
        long p95 = medidas.get(medidas.size() - 1);

        System.out.printf("Calendario: mes cargado en %d ms (peor de 10)%n", p95);

        assertThat(p95)
                .as("la rejilla completa de un mes cargado, sin paginar")
                .isLessThanOrEqualTo(400L);
    }
}
