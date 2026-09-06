package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Tocar el calendario invalida la revision de ese ano.
 *
 * <p>Es lo que impide el fallo silencioso: alguien anade un feriado despues de la
 * confirmacion y el sistema seguiria contando con datos que nadie reviso. Al
 * invalidar, vuelve a avisar hasta que una persona mire de nuevo.
 */
class CalendarInvalidationIT extends PostgresIntegrationTest {

    private static final int ANO = 2028;

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CalendarService calendario;
    @Autowired private CalendarReviewService revisiones;
    @Autowired private NonWorkingDayRepository dias;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        for (int i = 1; i <= 6; i++) {
            calendario.agregar(LocalDate.of(ANO, 1, i), "Dia " + i, Tipo.NATIONAL_HOLIDAY, jefa);
        }
        assertThat(revisiones.confirmar(ANO, revision(), true, false, jefa)).isEmpty();
        assertThat(calendario.cubierto(ANO)).isTrue();
    }

    private long revision() {
        return dias.revisionActual(ANO).orElse(0L);
    }

    @Test
    @DisplayName("agregar un dia invalida la revision del ano")
    void agregarInvalida() {
        calendario.agregar(LocalDate.of(ANO, 7, 28), "Fiestas Patrias", Tipo.NATIONAL_HOLIDAY, jefa);

        assertThat(calendario.cubierto(ANO))
                .as("el calendario cambio: hay que revisarlo otra vez").isFalse();
    }

    @Test
    @DisplayName("retirar un dia tambien invalida, y conserva su historia")
    void retirarInvalidaYConservaHistoria() {
        var primero = dias.delAno(ANO).get(0);
        UUID id = (UUID) primero.get("id");
        long version = ((Number) primero.get("version")).longValue();

        calendario.retirar(id, version, jefa);

        assertThat(calendario.cubierto(ANO)).isFalse();

        Integer evidencia = jdbc.sql("""
                SELECT count(*) FROM audit_event
                WHERE entity_type = 'NON_WORKING_DAY' AND entity_id = :id AND action = 'DELETE'
                """).param("id", id).query(Integer.class).single();
        assertThat(evidencia).as("retirar un dia no borra su historia").isEqualTo(1);
    }

    @Test
    @DisplayName("confirmar con una revision obsoleta se rechaza")
    void revisionObsoletaRechazada() {
        long observada = revision();
        // Otra persona anade un dia mientras esta pantalla estaba abierta.
        calendario.agregar(LocalDate.of(ANO, 12, 25), "Navidad", Tipo.NATIONAL_HOLIDAY, jefa);

        assertThatThrownBy(() -> revisiones.confirmar(ANO, observada, true, false, jefa))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class)
                .hasMessageContaining("cambio mientras revisaba");

        assertThat(calendario.cubierto(ANO)).isFalse();
    }

    @Test
    @DisplayName("tras revisar de nuevo, la cobertura vuelve")
    void revisarDeNuevoRestauraCobertura() {
        calendario.agregar(LocalDate.of(ANO, 5, 1), "Dia del trabajo", Tipo.NATIONAL_HOLIDAY, jefa);
        assertThat(calendario.cubierto(ANO)).isFalse();

        assertThat(revisiones.confirmar(ANO, revision(), true, false, jefa)).isEmpty();
        assertThat(calendario.cubierto(ANO)).isTrue();
    }

    @Test
    @DisplayName("cambiar un dia de ano deja ambos anos pendientes de revisar")
    void cambioDeAnoTocaAmbos() {
        int siguiente = ANO + 1;
        calendario.agregar(LocalDate.of(siguiente, 1, 1), "Ano nuevo", Tipo.NATIONAL_HOLIDAY, jefa);
        revisiones.confirmar(siguiente, dias.revisionActual(siguiente).orElse(0L), true, true, jefa);
        assertThat(calendario.cubierto(siguiente)).isTrue();

        var dia = dias.delAno(ANO).get(0);
        calendario.corregir((UUID) dia.get("id"), LocalDate.of(siguiente, 6, 15),
                "Movido de ano", Tipo.OTHER,
                ((Number) dia.get("version")).longValue(), jefa);

        assertThat(calendario.cubierto(ANO)).as("el ano de origen queda sin revisar").isFalse();
        assertThat(calendario.cubierto(siguiente)).as("el de destino tambien").isFalse();
    }
}
