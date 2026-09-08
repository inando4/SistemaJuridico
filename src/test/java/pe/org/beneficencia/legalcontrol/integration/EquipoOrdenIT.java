package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.team.SemanaDeTrabajo;
import pe.org.beneficencia.legalcontrol.team.TeamWorkloadRepository;

/**
 * El orden de la vista: primero quien mas tiene encima esta semana.
 *
 * <p>La segunda prueba es la que suele faltar: con la misma carga, el desempate lo
 * decidiria el motor y el orden podria cambiar entre dos aperturas. Una lista que
 * se baraja sola es una lista en la que no se confia.
 */
class EquipoOrdenIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TeamWorkloadRepository cargas;

    private LocalDate hoy;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        hoy = LocalDate.now();
    }

    private List<TeamWorkloadRepository.Fila> filas() {
        return cargas.cargaDelEquipo(hoy, SemanaDeTrabajo.lunesDe(hoy),
                SemanaDeTrabajo.domingoDe(hoy), hoy.minusDays(30));
    }

    @Test
    @DisplayName("el mas cargado de la semana sale primero")
    void elMasCargadoPrimero() {
        // sembrarCargaDesigual da al primero mas vencidos y al ultimo mas de la
        // semana; la suma decrece, que es justo lo que ordena.
        UUID a = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        UUID b = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        UUID c = SesionDePrueba.crearCuenta(jdbc, encoder, "c@ejemplo.test", "LAWYER");
        DatosSinteticos.sembrarCargaDesigual(jdbc, List.of(a, b, c), hoy);

        var orden = filas();

        for (int i = 1; i < orden.size(); i++) {
            int anterior = orden.get(i - 1).vencidos() + orden.get(i - 1).estaSemana();
            int actual = orden.get(i).vencidos() + orden.get(i).estaSemana();
            assertThat(anterior)
                    .as("la fila %d no puede tener menos carga que la %d", i - 1, i)
                    .isGreaterThanOrEqualTo(actual);
        }
    }

    @Test
    @DisplayName("lo vencido pesa en el orden tanto como lo que vence esta semana")
    void loVencidoCuentaEnLaCarga() {
        UUID conVencidos = SesionDePrueba.crearCuenta(jdbc, encoder, "v@ejemplo.test", "LAWYER");
        UUID conPocoDeEstaSemana =
                SesionDePrueba.crearCuenta(jdbc, encoder, "s@ejemplo.test", "LAWYER");

        var ahora = java.sql.Timestamp.from(java.time.Instant.now());
        UUID tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "T", 1,
                conVencidos, ahora).get(0);
        UUID prio = DatosSinteticos.sembrarCatalogo(jdbc, "priority", "P", 1,
                conVencidos, ahora).get(0);
        UUID est = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "E", 1,
                conVencidos, ahora).get(0);

        for (int i = 0; i < 5; i++) {
            DatosSinteticos.pendienteSuelto(jdbc, conVencidos, tipo, prio, est,
                    "Vencido " + i, hoy.minusDays(5), hoy.minusDays(20));
        }
        DatosSinteticos.pendienteSuelto(jdbc, conPocoDeEstaSemana, tipo, prio, est,
                "De esta semana", SemanaDeTrabajo.lunesDe(hoy), hoy.minusDays(2));

        assertThat(filas().get(0).id())
                .as("quince cosas vencidas pesan mas que dos de mañana")
                .isEqualTo(conVencidos);
    }

    @Test
    @DisplayName("con la misma carga, el orden es el mismo en dos aperturas")
    void ordenEstable() {
        for (int i = 1; i <= 4; i++) {
            SesionDePrueba.crearCuenta(jdbc, encoder, "igual" + i + "@ejemplo.test", "LAWYER");
        }

        var primera = filas().stream().map(TeamWorkloadRepository.Fila::id).toList();
        var segunda = filas().stream().map(TeamWorkloadRepository.Fila::id).toList();

        assertThat(segunda)
                .as("sin desempate por nombre e id, el motor decidiria y podria variar")
                .containsExactlyElementsOf(primera);
    }
}
