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

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureForm;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureService;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * El historial conserva valores anterior y nuevo, y distingue autor de responsable.
 */
class ProcedureHistoryIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AdministrativeProcedureService servicio;
    @Autowired private AuditQueryRepository historial;

    private UUID procedimiento;
    private UUID abogado;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        UUID idJefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(idJefa, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        procedimiento = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, requesting_area,
                                                      deadline, active, created_at, updated_at, version)
                VALUES (:id, :owner, 'ADM-HIST-2026', 'Original', DATE '2026-10-01',
                        true, :ahora, :ahora, 1)
                """).param("id", procedimiento).param("owner", abogado).param("ahora", ahora).update();
    }

    private AdministrativeProcedureForm form(String area, String limite, Long version) {
        return new AdministrativeProcedureForm(null, "ADM-HIST-2026", area, null, null,
                null, limite, null, version);
    }

    @Test
    @DisplayName("un cambio guarda el valor anterior y el nuevo")
    void guardaAntesYDespues() {
        servicio.editar(procedimiento, form("Modificado", "2026-11-15", 1L), jefa);

        var fila = jdbc.sql("""
                SELECT before_values::text AS antes, after_values::text AS despues
                FROM audit_event WHERE entity_id = :id
                """).param("id", procedimiento).query().singleRow();

        assertThat(fila.get("antes").toString()).contains("Original").contains("2026-10-01");
        assertThat(fila.get("despues").toString()).contains("Modificado").contains("2026-11-15");
    }

    @Test
    @DisplayName("el autor y el responsable se distinguen cuando son personas distintas")
    void autorDistintoDeResponsable() {
        servicio.editar(procedimiento, form("Corregido por jefatura", null, 1L), jefa);

        var entradas = historial.deEntidad("ADMINISTRATIVE_PROCEDURE", procedimiento, Paging.of(0));

        assertThat(entradas).hasSize(1);
        assertThat(entradas.get(0).actorName()).contains("jefa");
        assertThat(entradas.get(0).ownerId()).isEqualTo(abogado);
        assertThat(entradas.get(0).intervencionAjena()).isTrue();
    }

    @Test
    @DisplayName("el historial se lee del cambio mas reciente al mas antiguo")
    void ordenCronologicoInverso() {
        servicio.editar(procedimiento, form("Primero", null, 1L), jefa);
        long v = jdbc.sql("SELECT version FROM administrative_procedure WHERE id = :id")
                .param("id", procedimiento).query(Long.class).single();
        servicio.editar(procedimiento, form("Segundo", null, v), jefa);

        var entradas = historial.deEntidad("ADMINISTRATIVE_PROCEDURE", procedimiento, Paging.of(0));

        assertThat(entradas).hasSize(2);
        assertThat(entradas.get(0).afterValues()).contains("Segundo");
        assertThat(entradas.get(1).afterValues()).contains("Primero");
    }

    @Test
    @DisplayName("el historial de un procedimiento no mezcla el de los expedientes judiciales")
    void historialesSeparados() {
        servicio.editar(procedimiento, form("Modificado", null, 1L), jefa);

        var deProcedimiento = historial.deEntidad("ADMINISTRATIVE_PROCEDURE",
                procedimiento, Paging.of(0));
        var deJudicial = historial.deEntidad("JUDICIAL_CASE", procedimiento, Paging.of(0));

        assertThat(deProcedimiento).hasSize(1);
        assertThat(deJudicial).as("el mismo identificador no cruza tipos de entidad").isEmpty();
    }
}
