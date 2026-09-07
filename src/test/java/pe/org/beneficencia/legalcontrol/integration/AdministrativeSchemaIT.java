package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * La migracion V8 anade lo suyo sin debilitar lo que la 001 garantizaba.
 *
 * <p>Lo importante no es que las tablas nuevas existan, sino que <b>ampliar la
 * restriccion de auditoria no haya abierto una puerta</b>. Sustituir un CHECK es
 * una operacion de esquema, y conviene demostrar que no vino acompanada de un
 * cambio de privilegios.
 */
class AdministrativeSchemaIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    private Connection comoAplicacion() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "sistema_juridico_app", "test");
    }

    @Test
    @DisplayName("la migracion crea las tres tablas nuevas")
    void creaLasTablas() {
        List<String> tablas = jdbc.sql("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename IN ('administrative_procedure', 'administrative_status',
                                    'procedure_history_status_reference')
                ORDER BY tablename
                """).query(String.class).list();

        assertThat(tablas).containsExactly(
                "administrative_procedure", "administrative_status",
                "procedure_history_status_reference");
    }

    @Test
    @DisplayName("el catalogo administrativo arranca vacio")
    void catalogoVacio() {
        SesionDePrueba.limpiar(jdbc);
        Integer total = jdbc.sql("SELECT count(*) FROM administrative_status")
                .query(Integer.class).single();
        assertThat(total).isZero();
    }

    @Test
    @DisplayName("la restriccion ampliada admite los tipos nuevos y conserva los viejos")
    void restriccionAmpliada() {
        SesionDePrueba.limpiar(jdbc);
        UUID usuario = crearUsuario();

        for (String tipo : List.of("JUDICIAL_CASE", "APP_USER", "PROCEDURAL_STATUS",
                "NON_WORKING_DAY", "CALENDAR_REVIEW",
                "ADMINISTRATIVE_PROCEDURE", "ADMINISTRATIVE_STATUS")) {
            insertarEvidencia(usuario, tipo);
        }

        Integer escritas = jdbc.sql("SELECT count(*) FROM audit_event")
                .query(Integer.class).single();
        assertThat(escritas).isEqualTo(7);
    }

    @Test
    @DisplayName("un tipo de entidad inventado sigue rechazandose")
    void tipoInventadoRechazado() {
        SesionDePrueba.limpiar(jdbc);
        UUID usuario = crearUsuario();

        assertThatThrownBy(() -> insertarEvidencia(usuario, "ENTIDAD_INVENTADA"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("tras ampliar la restriccion, la aplicacion sigue sin poder tocar la evidencia")
    void inmutabilidadIntacta() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute("UPDATE audit_event SET action = 'falsificado'"))
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> st.execute("DELETE FROM audit_event"))
                    .hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("la referencia historica de estados administrativos tampoco se puede alterar")
    void referenciaHistoricaProtegida() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    "UPDATE procedure_history_status_reference SET audit_event_id = gen_random_uuid()"))
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> st.execute(
                    "DELETE FROM procedure_history_status_reference"))
                    .hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("la aplicacion tampoco puede alterar el esquema nuevo")
    void esquemaProtegido() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute("DROP TABLE administrative_procedure"))
                    .isInstanceOf(java.sql.SQLException.class);
        }

        Integer sigue = jdbc.sql("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tablename = 'administrative_procedure'
                """).query(Integer.class).single();
        assertThat(sigue).isEqualTo(1);
    }

    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO app_user (id, name, email, role, status, auth_version, version,
                                      created_by, created_at, updated_at)
                VALUES (:id, 'Prueba', 'esquema@ejemplo.test', 'HEAD', 'ACTIVE', 1, 1,
                        :id, :ahora, :ahora)
                """).param("id", id).param("ahora", ahora).update();
        return id;
    }

    private void insertarEvidencia(UUID usuario, String tipo) {
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
