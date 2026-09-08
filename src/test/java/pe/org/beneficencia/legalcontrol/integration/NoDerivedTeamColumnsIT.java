package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * La carga de trabajo no se guarda en ninguna parte (principio V).
 *
 * <p>Un agregado persistido envejece en silencio: nadie lo recalcula cuando se
 * cumple un pendiente, y acaba contradiciendo al listado al que enlaza. Esta prueba
 * mira el esquema, no el codigo, porque el codigo puede cambiar y la columna
 * quedarse.
 */
class NoDerivedTeamColumnsIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;

    @Test
    @DisplayName("no existe ninguna tabla de carga de trabajo")
    void sinTablaDeAgregados() {
        List<String> tablas = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND (table_name LIKE '%workload%' OR table_name LIKE '%carga%'
                       OR table_name LIKE '%team_%' OR table_name LIKE '%summary%')
                """).query(String.class).list();

        assertThat(tablas).isEmpty();
    }

    @Test
    @DisplayName("app_user no guarda recuentos de pendientes")
    void elUsuarioNoLlevaContadores() {
        List<String> columnas = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'app_user'
                """).query(String.class).list();

        assertThat(columnas)
                .as("un contador aqui envejeceria en cuanto alguien cumpla un pendiente")
                .noneMatch(c -> c.contains("count") || c.contains("total")
                        || c.contains("carga") || c.contains("workload")
                        || c.contains("pending"));
    }
}
