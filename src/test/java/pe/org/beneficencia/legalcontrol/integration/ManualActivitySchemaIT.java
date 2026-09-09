package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * La migracion V10 crea la actividad manual sin debilitar nada de lo anterior.
 *
 * <p>Tres cosas que no se pueden dar por supuestas y por eso se comprueban aqui:
 *
 * <ul>
 *   <li>que el tipo excluyente lo impida <b>la base</b>, no el validador;
 *   <li>que la aplicacion <b>no pueda borrar</b>. Es lo menos evidente: la V1 dejo
 *       {@code ALTER DEFAULT PRIVILEGES} concediendo DELETE sobre toda tabla nueva
 *       del esquema, asi que no otorgarlo no basta y hace falta revocarlo;
 *   <li>que ampliar la restriccion de auditoria por cuarta vez no haya perdido
 *       ninguno de los once valores anteriores.
 * </ul>
 */
class ManualActivitySchemaIT extends PostgresIntegrationTest {

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

    private UUID insertar(String descripcion, UUID tipoCatalogo, String otroTipo) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             pending_task_type_id, other_type,
                                             created_at, updated_at, version)
                VALUES (:id, :owner, :dia, :desc, :tipo, :otro, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", usuario).param("dia", LocalDate.now())
                .param("desc", descripcion).param("tipo", tipoCatalogo)
                .param("otro", otroTipo).param("ahora", ahora).update();
        return id;
    }

    @Test
    @DisplayName("la migracion crea la tabla")
    void creaLaTabla() {
        Integer existe = jdbc.sql("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tablename = 'manual_activity'
                """).query(Integer.class).single();

        assertThat(existe).isEqualTo(1);
    }

    @Test
    @DisplayName("las tres formas de tipo son validas para la base")
    void lasTresFormasDeTipo() {
        UUID tipo = catalogoDeTipos();

        assertThat(insertar("Sin tipo", null, null)).isNotNull();
        assertThat(insertar("Del catalogo", tipo, null)).isNotNull();
        assertThat(insertar("Escrito a mano", null, "Reunion con Contabilidad")).isNotNull();
    }

    @Test
    @DisplayName("rellenar las dos columnas de tipo lo impide la base")
    void tipoExcluyente() {
        UUID tipo = catalogoDeTipos();

        assertThatThrownBy(() -> insertar("Ambiguo", tipo, "Reunion"))
                .hasMessageContaining("manual_activity_tipo_excluyente");
    }

    @Test
    @DisplayName("una descripcion en blanco lo impide la base")
    void descripcionNoVacia() {
        assertThatThrownBy(() -> insertar("   ", null, null))
                .hasMessageContaining("manual_activity_descripcion_no_vacia");
    }

    @Test
    @DisplayName("la aplicacion puede leer, insertar y modificar, pero NO borrar")
    void sinPermisoDeBorrado() throws Exception {
        UUID id = insertar("Elaboracion de informe legal", null, null);

        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            // Lo que si puede: retirar es active = false, no un DELETE.
            st.executeUpdate("UPDATE manual_activity SET active = false WHERE id = '" + id + "'");

            assertThatThrownBy(() ->
                    st.executeUpdate("DELETE FROM manual_activity WHERE id = '" + id + "'"))
                    .as("la V1 concede DELETE por omision; la V10 tiene que revocarlo")
                    .hasMessageContaining("permission denied");
        }

        Integer sigue = jdbc.sql("SELECT count(*) FROM manual_activity WHERE id = :id")
                .param("id", id).query(Integer.class).single();
        assertThat(sigue).isEqualTo(1);
    }

    @Test
    @DisplayName("la aplicacion no puede tocar el historial de migraciones")
    void sinPermisoSobreFlyway() throws Exception {
        try (Connection app = comoAplicacion(); Statement st = app.createStatement()) {
            assertThatThrownBy(() ->
                    st.executeUpdate("DELETE FROM flyway_schema_history WHERE installed_rank = 1"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() ->
                    st.executeUpdate("UPDATE flyway_schema_history SET success = false"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    @DisplayName("la restriccion de auditoria admite el tipo nuevo y conserva los once")
    void auditoriaAmpliadaSinPerderNada() {
        String definicion = jdbc.sql("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'audit_event_entidad_valida'
                """).query(String.class).single();

        assertThat(definicion).contains("MANUAL_ACTIVITY");
        // Los once anteriores: el CHECK se sustituye, no se amplia, y perder uno
        // rompe en silencio la auditoria de esa entidad.
        assertThat(definicion).contains("APP_USER").contains("JUDICIAL_CASE")
                .contains("PROCEDURAL_STATUS").contains("NON_WORKING_DAY")
                .contains("CALENDAR_REVIEW").contains("ADMINISTRATIVE_PROCEDURE")
                .contains("ADMINISTRATIVE_STATUS").contains("PENDING_TASK")
                .contains("PENDING_TASK_TYPE").contains("PRIORITY")
                .contains("PENDING_TASK_STATUS");
    }

    @Test
    @DisplayName("no se puede borrar un tipo de catalogo que una actividad usa")
    void tipoProtegidoPorClaveForanea() {
        UUID tipo = catalogoDeTipos();
        insertar("Informe legal del mes", tipo, null);

        assertThatThrownBy(() ->
                jdbc.sql("DELETE FROM pending_task_type WHERE id = :id")
                        .param("id", tipo).update())
                .hasMessageContaining("manual_activity");
    }

    private UUID catalogoDeTipos() {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task_type (id, name, enabled, created_by,
                                               created_at, updated_at, version)
                VALUES (:id, 'Informe legal', true, :usuario, :ahora, :ahora, 1)
                """)
                .param("id", id).param("usuario", usuario).param("ahora", ahora).update();
        return id;
    }
}
