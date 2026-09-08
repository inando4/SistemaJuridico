package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.calendar.CalendarReviewService;
import pe.org.beneficencia.legalcontrol.calendar.CalendarService;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;

/**
 * Confirmar cobertura exige revision humana, no solo tener filas.
 *
 * <p>Con 0, 1, 4 y 5 entradas el comportamiento cambia: cero impide confirmar,
 * de una a cuatro avisa y exige un reconocimiento adicional, y cinco o mas sigue
 * exigiendo la declaracion explicita. Sin esto, cargar el 1 de enero bastaria
 * para que el sistema diera por bueno todo el ano.
 */
class CalendarCoverageIT extends PostgresIntegrationTest {

    private static final int ANO = 2027;

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CalendarService calendario;
    @Autowired private CalendarReviewService revisiones;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        jdbc.sql("DELETE FROM calendar_review").update();
        jdbc.sql("DELETE FROM non_working_day").update();
        jdbc.sql("DELETE FROM calendar_year").update();

        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private void cargar(int cuantos) {
        for (int i = 1; i <= cuantos; i++) {
            calendario.agregar(LocalDate.of(ANO, 1, i), "Dia " + i, Tipo.NATIONAL_HOLIDAY, jefa);
        }
    }

    private long revision() {
        return jdbc.sql("SELECT revision FROM calendar_year WHERE year = :a")
                .param("a", ANO).query(Long.class).optional().orElse(0L);
    }

    @Test
    @DisplayName("con cero entradas no se puede confirmar")
    void ceroImpideConfirmar() {
        var problema = revisiones.confirmar(ANO, 0, true, false, jefa);

        assertThat(problema).isPresent();
        assertThat(problema.get()).contains("ningún día registrado");
        assertThat(calendario.cubierto(ANO)).isFalse();
    }

    @Test
    @DisplayName("con una entrada avisa de cantidad baja y exige reconocerlo")
    void unaEntradaAvisa() {
        cargar(1);

        var sinReconocer = revisiones.confirmar(ANO, revision(), true, false, jefa);
        assertThat(sinReconocer).isPresent();
        assertThat(sinReconocer.get()).contains("inusualmente bajo");
        assertThat(calendario.cubierto(ANO)).isFalse();

        assertThat(revisiones.confirmar(ANO, revision(), true, true, jefa)).isEmpty();
        assertThat(calendario.cubierto(ANO)).isTrue();
    }

    @Test
    @DisplayName("con cuatro entradas sigue avisando")
    void cuatroEntradasAvisan() {
        cargar(4);
        assertThat(revisiones.confirmar(ANO, revision(), true, false, jefa))
                .get().asString().contains("inusualmente bajo");
    }

    @Test
    @DisplayName("con cinco entradas ya no avisa, pero sigue exigiendo la declaracion")
    void cincoEntradas() {
        cargar(5);

        var sinDeclarar = revisiones.confirmar(ANO, revision(), false, false, jefa);
        assertThat(sinDeclarar).get().asString().contains("calendario completo");
        assertThat(calendario.cubierto(ANO)).isFalse();

        assertThat(revisiones.confirmar(ANO, revision(), true, false, jefa)).isEmpty();
        assertThat(calendario.cubierto(ANO)).isTrue();
    }

    @Test
    @DisplayName("el servidor cuenta: no acepta un total enviado por el cliente")
    void elServidorCuenta() {
        cargar(2);
        // El cliente no tiene forma de decir «hay cinco»: el metodo no recibe cantidad.
        assertThat(revisiones.confirmar(ANO, revision(), true, false, jefa))
                .get().asString().contains("(2)");
    }
}
