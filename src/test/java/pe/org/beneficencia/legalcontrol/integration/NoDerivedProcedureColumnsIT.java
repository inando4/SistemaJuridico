package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Las tablas nuevas tampoco guardan valores derivados del tiempo.
 *
 * <p>Los dias restantes y la advertencia de fechas incoherentes cambian con el
 * calendario y con el dia. Guardarlos crea un dato que envejece en silencio y que
 * nadie recuerda actualizar.
 */
class NoDerivedProcedureColumnsIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    private List<String> columnasDe(String tabla) {
        return jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :tabla
                ORDER BY column_name
                """).param("tabla", tabla).query(String.class).list();
    }

    @Test
    @DisplayName("administrative_procedure solo guarda hechos, no interpretaciones")
    void sinColumnasDerivadas() {
        List<String> columnas = columnasDe("administrative_procedure");

        // Las fechas se guardan; su interpretacion se calcula al consultar.
        assertThat(columnas).contains("received_at", "deadline");
        assertThat(columnas).noneMatch(c ->
                c.contains("remaining") || c.contains("overdue") || c.contains("business")
                || c.contains("dias_") || c.contains("warning") || c.contains("inconsistent"));
    }

    @Test
    @DisplayName("el catalogo administrativo tampoco guarda contadores de uso")
    void catalogoSinContadores() {
        List<String> columnas = columnasDe("administrative_status");

        assertThat(columnas).containsExactlyInAnyOrder(
                "id", "name", "description", "enabled", "created_by",
                "created_at", "updated_at", "version");
    }

    @Test
    @DisplayName("la referencia historica solo ata evento y estado")
    void referenciaMinima() {
        assertThat(columnasDe("procedure_history_status_reference"))
                .containsExactlyInAnyOrder("audit_event_id", "administrative_status_id");
    }
}
