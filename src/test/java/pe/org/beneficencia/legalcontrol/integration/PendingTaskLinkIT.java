package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskForm;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskService;

/**
 * Un pendiente cuelga de un expediente judicial, de uno administrativo o de
 * ninguno (insumo, seccion 9). Nunca de los dos.
 *
 * <p>El ejemplo del propio insumo para el caso sin vinculo es «comprar toner para
 * impresora»: no todo lo que hay que hacer pertenece a un expediente.
 */
class PendingTaskLinkIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PendingTaskService servicio;

    private UUID abogado;
    private UUID judicial;
    private UUID administrativo;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        Timestamp ahora = Timestamp.from(Instant.now());

        judicial = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-LINK-2026', true, :ahora, :ahora, 1)
                """).param("id", judicial).param("owner", abogado).param("ahora", ahora).update();

        administrativo = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :owner, 'ADM-LINK-2026', true, :ahora, :ahora, 1)
                """).param("id", administrativo).param("owner", abogado)
                .param("ahora", ahora).update();
    }

    private PendingTaskForm form(String titulo, UUID j, UUID a) {
        return new PendingTaskForm(titulo, null, null, null, null, j, a,
                null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("se puede vincular a un expediente judicial")
    void vinculoJudicial() {
        var alta = servicio.crear(form("Elaborar escrito", judicial, null), abogado);

        assertThat(alta.correcto()).isTrue();
        UUID guardado = jdbc.sql("SELECT judicial_case_id FROM pending_task WHERE id = :id")
                .param("id", alta.id()).query(UUID.class).single();
        assertThat(guardado).isEqualTo(judicial);
    }

    @Test
    @DisplayName("se puede vincular a un procedimiento administrativo")
    void vinculoAdministrativo() {
        var alta = servicio.crear(form("Redactar informe", null, administrativo), abogado);

        assertThat(alta.correcto()).isTrue();
        UUID guardado = jdbc.sql("""
                SELECT administrative_procedure_id FROM pending_task WHERE id = :id
                """).param("id", alta.id()).query(UUID.class).single();
        assertThat(guardado).isEqualTo(administrativo);
    }

    @Test
    @DisplayName("un pendiente sin vinculo es valido")
    void sinVinculo() {
        var alta = servicio.crear(form("Comprar toner para impresora", null, null), abogado);

        assertThat(alta.correcto()).as("no todo lo que hay que hacer pertenece a un expediente")
                .isTrue();
    }

    @Test
    @DisplayName("vincular a los dos a la vez se rechaza")
    void vinculoDobleRechazado() {
        var alta = servicio.crear(form("Intento indebido", judicial, administrativo), abogado);

        assertThat(alta.correcto()).isFalse();
        assertThat(alta.errores().get("judicialCaseId")).contains("no de los dos");

        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        assertThat(total).isZero();
    }

    @Test
    @DisplayName("la base lo impide aunque se salte la aplicacion")
    void vinculoDobleImposibleEnLaBase() {
        Timestamp ahora = Timestamp.from(Instant.now());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                          administrative_procedure_id, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Saltandose la aplicacion', :j, :a, CURRENT_DATE,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("j", judicial).param("a", administrativo)
                .param("ahora", ahora).update())
                .as("la restriccion vive en la base, no solo en el validador")
                .isInstanceOf(Exception.class);
    }
}
