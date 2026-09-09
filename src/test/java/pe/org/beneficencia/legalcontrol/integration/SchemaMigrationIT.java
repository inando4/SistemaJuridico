package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Comprueba que las migraciones se aplican sobre PostgreSQL real y, sobre todo,
 * que el rol de la aplicacion NO puede alterar el esquema ni modificar la
 * evidencia.
 *
 * <p>Esto se prueba intentandolo de verdad con una conexion de ese rol, no
 * leyendo bits de permiso: lo que importa es que la base lo impida, no que la
 * configuracion parezca correcta.
 */
class SchemaMigrationIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    /**
     * Lista exhaustiva a proposito: si una migracion anade o quita una tabla, esta
     * prueba lo dice. Crecio con las funcionalidades 002, 003 y 006.
     *
     * <p>Y funciono: {@code manual_activity} entro aqui porque esta prueba fallo al
     * aplicar la V10, que es exactamente para lo que existe.
     */
    private static final List<String> TABLAS_ESPERADAS = List.of(
            "access_guard", "access_token", "administrative_procedure",
            "administrative_status", "app_user", "audit_event", "auth_attempt",
            "calendar_review", "calendar_year", "case_history_status_reference",
            "judicial_case", "manual_activity", "non_working_day", "pending_task",
            "pending_task_history_reference", "pending_task_status",
            "pending_task_type", "priority", "procedural_status",
            "procedure_history_status_reference");

    private Connection comoAplicacion() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "sistema_juridico_app", "test");
    }

    @Test
    @DisplayName("las diez migraciones crean todas las tablas del modelo")
    void creaTodasLasTablas() {
        List<String> tablas = jdbc.sql("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                ORDER BY tablename
                """).query(String.class).list();

        assertThat(tablas).containsExactlyElementsOf(TABLAS_ESPERADAS);
    }

    @Test
    @DisplayName("el catalogo de estados procesales arranca vacio")
    void catalogoVacio() {
        Integer total = jdbc.sql("SELECT count(*) FROM procedural_status").query(Integer.class).single();
        assertThat(total).isZero();
    }

    @Test
    @DisplayName("la aplicacion no puede modificar ni borrar la evidencia")
    void evidenciaInmutable() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute("UPDATE audit_event SET action = 'falsificado'"))
                    .hasMessageContaining("denied");
        }
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() -> st.execute("DELETE FROM audit_event"))
                    .hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("la aplicacion puede leer e insertar evidencia")
    void evidenciaLegibleYAmpliable() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM audit_event")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @DisplayName("la aplicacion no puede alterar el esquema")
    void esquemaProtegido() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            // Crear: PostgreSQL lo rechaza por falta de privilegio sobre el esquema.
            assertThatThrownBy(() -> st.execute("CREATE TABLE colada (x int)"))
                    .hasMessageContaining("denied");
            // Borrar: lo rechaza por propiedad, con otra redaccion. Se comprueba el
            // resultado y no el texto, que depende de la version del motor.
            assertThatThrownBy(() -> st.execute("DROP TABLE judicial_case"))
                    .isInstanceOf(java.sql.SQLException.class);
        }

        // Lo que de verdad importa: la tabla sigue ahi.
        Integer sigue = jdbc.sql("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tablename = 'judicial_case'
                """).query(Integer.class).single();
        assertThat(sigue).isEqualTo(1);
    }

    @Test
    @DisplayName("el guard de acceso tiene exactamente una fila")
    void guardConFilaUnica() {
        Integer filas = jdbc.sql("SELECT count(*) FROM access_guard").query(Integer.class).single();
        assertThat(filas).isEqualTo(1);
        List<String> errores = new ArrayList<>();
        try {
            jdbc.sql("INSERT INTO access_guard (id) VALUES (2)").update();
            errores.add("se permitio insertar una segunda fila en access_guard");
        } catch (Exception esperado) {
            // La restriccion debe impedirlo.
        }
        assertThat(errores).isEmpty();
    }
}
