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

import pe.org.beneficencia.legalcontrol.calendar.CalendarService;
import pe.org.beneficencia.legalcontrol.config.SeedHolidaysCommand;

/**
 * La carga de feriados propone fechas; no da por revisado ningun ano.
 *
 * <p>Lo que se comprueba aqui es sobre todo lo que el comando <b>no</b> hace:
 * no confirma la cobertura, no repone un dia que la jefa retiro y no pisa una
 * descripcion que corrigio. Un comando que carga datos legales sin barrera humana
 * seria peor que no tenerlo, porque el aviso de «calendario sin revisar»
 * desapareceria sin que nadie hubiera mirado nada.
 */
class SeedHolidaysIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SeedHolidaysCommand carga;
    @Autowired private CalendarService calendario;

    private static final int ANO = 2027;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
    }

    private List<LocalDate> diasDe(int ano) {
        return jdbc.sql("""
                SELECT day FROM non_working_day
                WHERE EXTRACT(YEAR FROM day) = :ano ORDER BY day
                """).param("ano", ano).query(LocalDate.class).list();
    }

    // ------------------------------------------------------------- la carga basica

    @Test
    @DisplayName("sin cuenta de jefatura no carga nada y lo explica")
    void sinJefaturaNoCarga() {
        String informe = carga.ejecutar(List.of(ANO));

        assertThat(informe).contains("bootstrap");
        assertThat(diasDe(ANO)).isEmpty();
    }

    @Test
    @DisplayName("carga los diecisiete dias del ano, con Semana Santa calculada")
    void cargaElAno() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar(List.of(ANO));

        assertThat(diasDe(ANO)).hasSize(17);
        assertThat(diasDe(ANO)).contains(
                LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 26),  // Semana Santa
                LocalDate.of(2027, 7, 28), LocalDate.of(2027, 8, 15));
    }

    @Test
    @DisplayName("carga varios anos de una vez")
    void cargaVariosAnos() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar(List.of(2026, 2027));

        assertThat(diasDe(2026)).hasSize(17);
        assertThat(diasDe(2027)).hasSize(17);
    }

    @Test
    @DisplayName("los dias quedan atribuidos a la jefatura y auditados")
    void quedanAtribuidosYAuditados() {
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar(List.of(ANO));

        Integer ajenos = jdbc.sql("""
                SELECT count(*) FROM non_working_day
                WHERE EXTRACT(YEAR FROM day) = :ano AND created_by <> :jefa
                """).param("ano", ANO).param("jefa", jefa).query(Integer.class).single();
        assertThat(ajenos).isZero();

        Integer eventos = jdbc.sql("""
                SELECT count(*) FROM audit_event
                WHERE entity_type = 'NON_WORKING_DAY' AND action = 'CREATE'
                """).query(Integer.class).single();
        assertThat(eventos)
                .as("quien pregunte de donde salio una fecha tiene que poder averiguarlo")
                .isEqualTo(17);

        assertThat(jdbc.sql("""
                SELECT reason FROM audit_event
                WHERE entity_type = 'NON_WORKING_DAY' LIMIT 1
                """).query(String.class).single())
                .as("la evidencia debe decir que la fecha aun no esta revisada")
                .contains("seed-holidays");
    }

    // --------------------------------------------------- la barrera humana intacta

    @Test
    @DisplayName("el ano NO queda confirmado: la revision sigue siendo de una persona")
    void noConfirmaLaCobertura() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar(List.of(ANO));

        assertThat(jdbc.sql("SELECT count(*) FROM calendar_review").query(Integer.class).single())
                .as("escribir esa fila afirmaria que alguien comprobo lo que trajo un programa")
                .isZero();
        assertThat(calendario.cubierto(ANO))
                .as("con dias pero sin revision, el ano sigue sin cubrir")
                .isFalse();
    }

    @Test
    @DisplayName("cargar un ano no revive una revision anterior que quedo suelta")
    void noActivaUnaRevisionVieja() {
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        // Un ano con revision confirmada pero sin ningun dia: no esta cubierto,
        // porque la cobertura exige las dos cosas a la vez.
        jdbc.sql("""
                INSERT INTO calendar_year (year, revision, created_by, created_at)
                VALUES (:ano, 1, :jefa, now())
                """).param("ano", ANO).param("jefa", jefa).update();
        jdbc.sql("""
                INSERT INTO calendar_review (id, year, reviewed_revision, reviewed_by,
                                             reviewed_at, full_year_reviewed)
                VALUES (:id, :ano, 1, :jefa, now(), true)
                """).param("id", UUID.randomUUID()).param("ano", ANO)
                .param("jefa", jefa).update();
        assertThat(calendario.cubierto(ANO)).isFalse();

        carga.ejecutar(List.of(ANO));

        assertThat(calendario.cubierto(ANO))
                .as("anadir dias bajo una revision vieja daria por revisado lo que nadie miro")
                .isFalse();
    }

    // ------------------------------------------------------------- no pisa nada

    @Test
    @DisplayName("volver a ejecutarlo no duplica ni cambia nada")
    void esIdempotente() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar(List.of(ANO));
        var primera = diasDe(ANO);
        String informe = carga.ejecutar(List.of(ANO));

        assertThat(diasDe(ANO)).containsExactlyElementsOf(primera);
        assertThat(informe).contains("ya tenia 17 dias");
    }

    @Test
    @DisplayName("un ano con dias puestos a mano se deja entero como esta")
    void noTocaUnAnoQueYaTieneDias() {
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        // La jefa anadio a mano un puente por decreto supremo, que el comando no sabe.
        jdbc.sql("""
                INSERT INTO non_working_day (id, day, description, kind, created_by,
                                             created_at, updated_at, version)
                VALUES (:id, :dia, 'Puente por decreto supremo', 'NON_WORKING_DAY',
                        :jefa, now(), now(), 1)
                """).param("id", UUID.randomUUID())
                .param("dia", LocalDate.of(ANO, 7, 30)).param("jefa", jefa).update();

        String informe = carga.ejecutar(List.of(ANO));

        assertThat(diasDe(ANO))
                .as("carga anos vacios; completar uno a medias es trabajo de la pantalla")
                .containsExactly(LocalDate.of(ANO, 7, 30));
        assertThat(informe).contains("se dejo intacto");
    }

    @Test
    @DisplayName("no repone un dia que la jefatura retiro")
    void noReponeLoRetirado() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        carga.ejecutar(List.of(ANO));

        // La jefa decide que el 15 de agosto no aplica a su caso y lo retira.
        LocalDate arequipa = LocalDate.of(ANO, 8, 15);
        jdbc.sql("DELETE FROM non_working_day WHERE day = :dia").param("dia", arequipa).update();

        String informe = carga.ejecutar(List.of(ANO));

        assertThat(diasDe(ANO))
                .as("un comando de carga no revierte una decision del area")
                .doesNotContain(arequipa);
        assertThat(informe).contains("ya tenia 16 dias");
    }

    @Test
    @DisplayName("no pisa la descripcion ni el tipo que la jefatura corrigio")
    void noPisaLoCorregido() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        carga.ejecutar(List.of(ANO));

        LocalDate navidad = LocalDate.of(ANO, 12, 25);
        jdbc.sql("""
                UPDATE non_working_day SET description = 'Navidad (medio día)', kind = 'OTHER'
                WHERE day = :dia
                """).param("dia", navidad).update();

        carga.ejecutar(List.of(ANO));

        var fila = jdbc.sql("SELECT description, kind FROM non_working_day WHERE day = :dia")
                .param("dia", navidad).query().singleRow();
        assertThat(fila.get("description")).isEqualTo("Navidad (medio día)");
        assertThat(fila.get("kind")).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("una segunda ejecucion sin cambios no invalida la revision de la jefatura")
    void sinCambiosNoInvalidaNada() {
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        carga.ejecutar(List.of(ANO));

        // La jefa revisa y confirma la cobertura del ano ya cargado.
        long revision = jdbc.sql("SELECT revision FROM calendar_year WHERE year = :ano")
                .param("ano", ANO).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO calendar_review (id, year, reviewed_revision, reviewed_by,
                                             reviewed_at, full_year_reviewed)
                VALUES (:id, :ano, :rev, :jefa, now(), true)
                """).param("id", UUID.randomUUID()).param("ano", ANO)
                .param("rev", revision).param("jefa", jefa).update();
        assertThat(calendario.cubierto(ANO)).isTrue();

        carga.ejecutar(List.of(ANO));

        assertThat(calendario.cubierto(ANO))
                .as("no cambio nada: volver a pedir revision seria un aviso falso")
                .isTrue();
    }
}
