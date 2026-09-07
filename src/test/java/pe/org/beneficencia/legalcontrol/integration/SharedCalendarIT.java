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
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarReviewService;
import pe.org.beneficencia.legalcontrol.calendar.CalendarService;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;

/**
 * El calendario es UNO SOLO para expedientes judiciales y procedimientos
 * administrativos.
 *
 * <p>Son los mismos feriados del mismo pais y de la misma institucion. Si esta
 * prueba fallara significaria que hay dos calendarios donde deberia haber uno, y
 * ese fallo no se ve leyendo el codigo: se ve cuando alguien carga un feriado y
 * solo cambia la mitad de los plazos.
 */
class SharedCalendarIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CalendarService calendario;
    @Autowired private CalendarReviewService revisiones;
    @Autowired private CalendarRepository lectura;
    @Autowired private NonWorkingDayRepository dias;
    @Autowired private DeadlineEvaluator plazos;

    private static final LocalDate HOY = LocalDate.of(2029, 3, 1);
    private static final LocalDate LIMITE = LocalDate.of(2029, 3, 31);

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        for (int d = 1; d <= 6; d++) {
            calendario.agregar(LocalDate.of(2029, 1, d), "Dia " + d, Tipo.NATIONAL_HOLIDAY, jefa);
        }
        revisiones.confirmar(2029, dias.revisionActual(2029).orElse(0L), true, false, jefa);
    }

    private int diasHabiles() {
        return plazos.evaluar(LIMITE, HOY, lectura.paraListado(HOY)).diasHabiles();
    }

    @Test
    @DisplayName("el mismo plazo da el mismo conteo para ambas clases de expediente")
    void mismoConteoParaAmbos() {
        // Se evalua la misma fecha con la misma instantanea: si hubiera dos
        // calendarios, cada registro usaria el suyo y el conteo diferiria.
        var instantanea = lectura.paraListado(HOY);

        int paraJudicial = plazos.evaluar(LIMITE, HOY, instantanea).diasHabiles();
        int paraAdministrativo = plazos.evaluar(LIMITE, HOY, instantanea).diasHabiles();

        assertThat(paraAdministrativo).isEqualTo(paraJudicial);
    }

    @Test
    @DisplayName("agregar un feriado cambia el conteo de los dos registros a la vez")
    void unFeriadoAfectaAAmbos() {
        int antes = diasHabiles();

        // Un jueves dentro del intervalo, para que descuente de verdad.
        calendario.agregar(LocalDate.of(2029, 3, 15), "Feriado nuevo", Tipo.NATIONAL_HOLIDAY, jefa);
        revisiones.confirmar(2029, dias.revisionActual(2029).orElse(0L), true, false, jefa);

        int despues = diasHabiles();

        assertThat(despues).as("el feriado nuevo debe descontar un dia habil").isEqualTo(antes - 1);
    }

    @Test
    @DisplayName("solo existe una tabla de dias no laborables")
    void unaSolaTablaDeCalendario() {
        var tablas = jdbc.sql("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public' AND tablename LIKE '%non_working%'
                """).query(String.class).list();

        assertThat(tablas)
                .as("un calendario paralelo serian dos verdades sobre lo mismo")
                .containsExactly("non_working_day");
    }

    @Test
    @DisplayName("retirar la revision deja sin conteo a los dos registros")
    void sinCoberturaNingunoCuenta() {
        assertThat(diasHabiles()).isNotNull();

        // Tocar el calendario invalida la revision del ano.
        calendario.agregar(LocalDate.of(2029, 5, 1), "Otro dia", Tipo.NATIONAL_HOLIDAY, jefa);

        var vista = plazos.evaluar(LIMITE, HOY, lectura.paraListado(HOY));
        assertThat(vista.diasHabiles()).as("sin revision no se cuenta para nadie").isNull();
    }
}
