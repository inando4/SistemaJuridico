package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * La migracion V9 anade lo suyo sin debilitar lo anterior.
 *
 * <p>Lo decisivo: que el vinculo excluyente lo impida <b>la base</b> y no solo la
 * aplicacion, y que ampliar la restriccion de auditoria por tercera vez no haya
 * abierto ninguna puerta.
 */
class PendingTaskSchemaIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID usuario;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
    }

    private Connection comoAplicacion() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "sistema_juridico_app", "test");
    }

    @Test
    @DisplayName("la migracion crea las cinco tablas nuevas")
    void creaLasTablas() {
        List<String> tablas = jdbc.sql("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public' AND tablename IN
                  ('pending_task', 'pending_task_type', 'priority',
                   'pending_task_status', 'pending_task_history_reference')
                ORDER BY tablename
                """).query(String.class).list();

        assertThat(tablas).containsExactly("pending_task", "pending_task_history_reference",
                "pending_task_status", "pending_task_type", "priority");
    }

    @Test
    @DisplayName("los tres catalogos arrancan vacios")
    void catalogosVacios() {
        for (String tabla : List.of("pending_task_type", "priority", "pending_task_status")) {
            Integer total = jdbc.sql("SELECT count(*) FROM " + tabla)
                    .query(Integer.class).single();
            assertThat(total).as("%s debe arrancar vacio", tabla).isZero();
        }
    }

    @Test
    @DisplayName("la base impide vincular a un expediente judicial y a uno administrativo a la vez")
    void vinculoExcluyente() {
        UUID judicial = crearExpedienteJudicial();
        UUID administrativo = crearProcedimientoAdministrativo();

        // Uno solo: se acepta.
        insertarPendiente("Solo judicial", judicial, null);
        insertarPendiente("Solo administrativo", null, administrativo);
        insertarPendiente("Sin vinculo", null, null);

        // Los dos: lo rechaza la base, no la aplicacion.
        assertThatThrownBy(() -> insertarPendiente("Los dos", judicial, administrativo))
                .isInstanceOf(Exception.class);

        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        assertThat(total).isEqualTo(3);
    }

    @Test
    @DisplayName("la restriccion ampliada admite los tipos nuevos y conserva los anteriores")
    void restriccionAmpliada() {
        for (String tipo : List.of("JUDICIAL_CASE", "ADMINISTRATIVE_PROCEDURE", "APP_USER",
                "PENDING_TASK", "PENDING_TASK_TYPE", "PRIORITY", "PENDING_TASK_STATUS")) {
            insertarEvidencia(tipo);
        }

        Integer escritas = jdbc.sql("SELECT count(*) FROM audit_event")
                .query(Integer.class).single();
        assertThat(escritas).isEqualTo(7);
    }

    @Test
    @DisplayName("tras ampliar la restriccion, la aplicacion sigue sin poder tocar la evidencia")
    void inmutabilidadIntacta() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute("UPDATE audit_event SET action = 'falsificado'"))
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> st.execute("DELETE FROM audit_event"))
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> st.execute(
                    "DELETE FROM pending_task_history_reference")).hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("la aplicacion tampoco puede alterar el esquema nuevo")
    void esquemaProtegido() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute("DROP TABLE pending_task"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
        Integer sigue = jdbc.sql("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tablename = 'pending_task'
                """).query(Integer.class).single();
        assertThat(sigue).isEqualTo(1);
    }

    private UUID crearExpedienteJudicial() {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-VINC-2026', true, :ahora, :ahora, 1)
                """).param("id", id).param("owner", usuario).param("ahora", ahora).update();
        return id;
    }

    private UUID crearProcedimientoAdministrativo() {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :owner, 'ADM-VINC-2026', true, :ahora, :ahora, 1)
                """).param("id", id).param("owner", usuario).param("ahora", ahora).update();
        return id;
    }

    private void insertarPendiente(String titulo, UUID judicial, UUID administrativo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                          administrative_procedure_id, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :judicial, :administrativo, :hoy,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", usuario).param("titulo", titulo)
                .param("judicial", judicial).param("administrativo", administrativo)
                .param("hoy", LocalDate.now()).param("ahora", ahora)
                .update();
    }

    private void insertarEvidencia(String tipo) {
        jdbc.sql("""
                INSERT INTO audit_event (id, entity_type, entity_id, action, actor_id,
                                         owner_id, occurred_at, after_values)
                VALUES (:id, :tipo, :entidad, 'CREATE', :usuario, :usuario, :ahora,
                        CAST('{}' AS jsonb))
                """)
                .param("id", UUID.randomUUID()).param("tipo", tipo)
                .param("entidad", UUID.randomUUID()).param("usuario", usuario)
                .param("ahora", Timestamp.from(Instant.now()))
                .update();
    }
}
