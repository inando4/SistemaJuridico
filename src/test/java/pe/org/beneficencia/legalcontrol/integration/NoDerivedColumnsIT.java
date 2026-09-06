package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Ningun valor derivado se persiste (principio V).
 *
 * <p>Los dias restantes, el estado de vencimiento o la cantidad de feriados
 * cambian cada dia. Guardarlos crea un dato que envejece en silencio y que nadie
 * recuerda actualizar: el sistema acabaria mostrando plazos de la semana pasada
 * con toda la confianza del mundo.
 */
class NoDerivedColumnsIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;

    private static final List<String> PROHIBIDAS = List.of(
            "days_remaining", "dias_restantes", "remaining_days", "business_days",
            "is_overdue", "overdue", "deadline_status", "status_temporal",
            "holiday_count", "non_working_count", "coverage", "is_covered", "covered",
            "workload", "carga");

    @Test
    @DisplayName("no existe ninguna columna que guarde un valor derivado del tiempo")
    void sinColumnasDerivadas() {
        List<String> columnas = jdbc.sql("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                ORDER BY 1
                """).query(String.class).list();

        for (String prohibida : PROHIBIDAS) {
            assertThat(columnas)
                    .as("la columna %s guardaria un valor que cambia cada dia", prohibida)
                    .noneMatch(c -> c.toLowerCase().endsWith("." + prohibida));
        }
    }

    @Test
    @DisplayName("judicial_case solo guarda hechos, no interpretaciones")
    void expedienteSoloGuardaHechos() {
        List<String> columnas = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'judicial_case'
                ORDER BY column_name
                """).query(String.class).list();

        // La fecha limite se guarda; su interpretacion se calcula al consultar.
        assertThat(columnas).contains("deadline");
        assertThat(columnas).noneMatch(c -> c.contains("remaining") || c.contains("overdue"));
    }

    @Test
    @DisplayName("calendar_year no guarda el conteo de dias ni si esta cubierto")
    void calendarioSinContadores() {
        List<String> columnas = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'calendar_year'
                """).query(String.class).list();

        assertThat(columnas)
                .as("la cobertura se deduce de las revisiones, no se guarda")
                .containsExactlyInAnyOrder("year", "revision", "created_by", "created_at");
    }
}
