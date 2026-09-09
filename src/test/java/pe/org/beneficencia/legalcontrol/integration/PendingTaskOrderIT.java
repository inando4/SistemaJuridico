package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.pendingtask.PendingTask;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskFilters;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskRepository;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * El orden «por hacer primero» de la ficha del expediente.
 *
 * <p>Contra PostgreSQL de verdad: el comportamiento de {@code (x IS NULL) DESC} y
 * de {@code NULLS LAST} es del motor, no de la aplicacion.
 */
class PendingTaskOrderIT extends PostgresIntegrationTest {

    @Autowired private PendingTaskRepository pendientes;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID expediente;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");

        expediente = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-ORDEN-2026', true, :a, :a, 1)
                """).param("id", expediente).param("o", abogado)
                .param("a", Timestamp.from(Instant.now())).update();

        // A proposito insertados en desorden: si el ORDER BY no hiciera nada, el
        // resultado saldria en este mismo orden y la prueba lo veria.
        pendiente(abogado, "Cumplido con plazo temprano", "2026-01-10", true);
        pendiente(abogado, "Por hacer sin plazo", null, false);
        pendiente(abogado, "Por hacer con plazo tardio", "2026-12-31", false);
        pendiente(abogado, "Cumplido sin plazo", null, true);
        pendiente(abogado, "Por hacer con plazo temprano", "2026-02-01", false);
    }

    private void pendiente(UUID owner, String titulo, String limite, boolean cumplido) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id, registered_at,
                                          deadline, completed_at, active,
                                          created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :hoy, CAST(:lim AS date), :fin, true, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("j", expediente).param("hoy", LocalDate.now()).param("lim", limite)
                .param("fin", cumplido ? ahora : null).param("ts", ahora).update();
    }

    private List<String> titulos(PendingTaskFilters filtros, Paging pagina) {
        List<String> nombres = new ArrayList<>();
        for (PendingTask t : pendientes.listar(filtros, pagina, LocalDate.now())) {
            nombres.add(t.title());
        }
        return nombres;
    }

    @Test
    @DisplayName("lo que queda por hacer va delante de lo cumplido (RF-002)")
    void loPendienteDelante() {
        List<String> orden = titulos(PendingTaskFilters.deExpedienteJudicial(expediente),
                Paging.of(0));

        assertThat(orden).hasSize(5);
        assertThat(orden.subList(0, 3))
                .as("los tres por hacer ocupan las tres primeras posiciones")
                .containsExactly("Por hacer con plazo temprano",
                                 "Por hacer con plazo tardio",
                                 "Por hacer sin plazo");
        assertThat(orden.subList(3, 5))
                .as("los cumplidos van despues, tambien por fecha limite")
                .containsExactly("Cumplido con plazo temprano", "Cumplido sin plazo");
    }

    @Test
    @DisplayName("dentro de cada grupo manda la fecha limite, con los sin plazo al final")
    void sinPlazoAlFinalDeSuGrupo() {
        List<String> orden = titulos(PendingTaskFilters.deExpedienteJudicial(expediente),
                Paging.of(0));

        assertThat(orden.indexOf("Por hacer sin plazo"))
                .as("NULLS LAST: sin plazo no significa urgente")
                .isGreaterThan(orden.indexOf("Por hacer con plazo tardio"));
    }

    @Test
    @DisplayName("con fechas limite empatadas la paginacion no repite ni se salta filas")
    void elDesempatePorIdSostieneLaPaginacion() {
        UUID abogado = jdbc.sql("SELECT id FROM app_user WHERE email = 'yo@ejemplo.test'")
                .query(UUID.class).single();
        // Diez con exactamente la misma fecha limite: sin el desempate por t.id, el
        // motor puede devolverlos en distinto orden en cada consulta, y entonces
        // LIMIT/OFFSET repite unos y se salta otros.
        for (int i = 0; i < 10; i++) {
            pendiente(abogado, "Empatado " + i, "2026-06-15", false);
        }

        List<String> primera = titulos(PendingTaskFilters.deExpedienteJudicial(expediente),
                new Paging(0, 5));
        List<String> segunda = titulos(PendingTaskFilters.deExpedienteJudicial(expediente),
                new Paging(1, 5));

        assertThat(primera).hasSize(6);   // cinco mas el sondeo
        assertThat(segunda).isNotEmpty();
        assertThat(primera.subList(0, 5))
                .as("ninguna fila de la primera pagina reaparece en la segunda")
                .doesNotContainAnyElementsOf(segunda);
    }

    @Test
    @DisplayName("la ficha usa notArchived: el cumplido esta, el archivado no")
    void elConjuntoDeLaFicha() {
        UUID abogado = jdbc.sql("SELECT id FROM app_user WHERE email = 'yo@ejemplo.test'")
                .query(UUID.class).single();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :o, 'Retirado del expediente', :j, :hoy, false, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", abogado).param("j", expediente)
                .param("hoy", LocalDate.now()).param("ts", ahora).update();

        List<String> orden = titulos(PendingTaskFilters.deExpedienteJudicial(expediente),
                Paging.of(0));

        assertThat(orden)
                .contains("Cumplido sin plazo")
                .doesNotContain("Retirado del expediente");
    }
}
