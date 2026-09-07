package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Las tablas de pendientes no guardan ningun valor derivado del tiempo.
 *
 * <p>La antiguedad, los dias restantes, el tiempo de atencion y el numero de
 * reprogramaciones cambian con el calendario o con el dia. Guardarlos crea un dato
 * que envejece en silencio y que nadie recuerda actualizar.
 */
class NoDerivedPendingColumnsIT extends PostgresIntegrationTest {

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
    @DisplayName("pending_task solo guarda hechos, no interpretaciones")
    void sinColumnasDerivadas() {
        List<String> columnas = columnasDe("pending_task");

        // Las fechas y el instante de cumplimiento son hechos; su interpretacion no.
        assertThat(columnas).contains("received_at", "scheduled_for", "deadline", "completed_at");

        assertThat(columnas).noneMatch(c ->
                c.contains("remaining") || c.contains("overdue") || c.contains("age")
                || c.contains("antiguedad") || c.contains("dias_")
                || c.contains("reschedul") || c.contains("reprogramaciones")
                || c.contains("attention_time") || c.contains("business_days"));
    }

    @Test
    @DisplayName("tampoco guarda un contador de reprogramaciones")
    void sinContadorDeReprogramaciones() {
        // Se cuenta desde el historial: un contador guardado se desincronizaria en
        // cuanto alguien revirtiera un cumplido.
        assertThat(columnasDe("pending_task")).noneMatch(c -> c.contains("count"));
    }

    @Test
    @DisplayName("los tres catalogos nuevos no guardan contadores de uso")
    void catalogosSinContadores() {
        for (String tabla : List.of("pending_task_type", "priority", "pending_task_status")) {
            assertThat(columnasDe(tabla)).as("%s", tabla).containsExactlyInAnyOrder(
                    "id", "name", "description", "enabled", "created_by",
                    "created_at", "updated_at", "version");
        }
    }

    @Test
    @DisplayName("ninguna columna del sistema guarda un archivo")
    void ningunArchivo() {
        List<String> todas = jdbc.sql("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                """).query(String.class).list();

        // La seccion 3.5 excluye documentos: el documento de salida es un numero.
        assertThat(todas).noneMatch(c ->
                c.contains("file_content") || c.contains("attachment") || c.contains("blob")
                || c.contains("adjunto") || c.contains("archivo"));
    }
}
