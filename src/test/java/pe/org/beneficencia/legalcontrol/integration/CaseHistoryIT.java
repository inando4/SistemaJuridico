package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseForm;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseService;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * El historial conserva valores anterior y nuevo, distingue autor de responsable,
 * y no se puede tocar.
 */
class CaseHistoryIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JudicialCaseService servicio;
    @Autowired private AuditQueryRepository historial;

    private UUID expediente;
    private UUID abogado;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        UUID idJefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(idJefa, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        expediente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject, deadline,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-HIST-2026', 'Original', DATE '2026-10-01',
                        true, :ahora, :ahora, 1)
                """)
                .param("id", expediente).param("owner", abogado).param("ahora", ahora).update();
    }

    @Test
    @DisplayName("un cambio guarda el valor anterior y el nuevo")
    void guardaAntesYDespues() {
        servicio.editar(expediente, new JudicialCaseForm(null, "EXP-HIST-2026", null, null,
                "Modificado", null, null, null, null, "2026-11-15", null, null, null, null,
                1L), jefa);

        var fila = jdbc.sql("""
                SELECT before_values::text AS antes, after_values::text AS despues
                FROM audit_event WHERE entity_id = :id
                """).param("id", expediente).query().singleRow();

        assertThat(fila.get("antes").toString()).contains("Original").contains("2026-10-01");
        assertThat(fila.get("despues").toString()).contains("Modificado").contains("2026-11-15");
    }

    @Test
    @DisplayName("el autor y el responsable se distinguen cuando son personas distintas")
    void autorDistintoDeResponsable() {
        servicio.editar(expediente, new JudicialCaseForm(null, "EXP-HIST-2026", null, null,
                "Corregido por jefatura", null, null, null, null, null, null, null, null, null,
                1L), jefa);

        var entradas = historial.deEntidad("JUDICIAL_CASE", expediente, Paging.of(0));

        assertThat(entradas).hasSize(1);
        var entrada = entradas.get(0);
        assertThat(entrada.actorName()).contains("jefa");
        assertThat(entrada.ownerId()).isEqualTo(abogado);
        assertThat(entrada.intervencionAjena()).as("debe marcarse como intervencion").isTrue();
    }

    @Test
    @DisplayName("nadie puede editar ni borrar entradas del historial")
    void historialInmutable() throws Exception {
        servicio.editar(expediente, new JudicialCaseForm(null, "EXP-HIST-2026", null, null,
                "Modificado", null, null, null, null, null, null, null, null, null,
                1L), jefa);

        try (Connection app = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "sistema_juridico_app", "test");
             Statement st = app.createStatement()) {

            assertThatThrownBy(() -> st.execute(
                    "UPDATE audit_event SET action = 'CREATE'")).hasMessageContaining("denied");
            assertThatThrownBy(() -> st.execute(
                    "DELETE FROM audit_event")).hasMessageContaining("denied");
        }

        Integer siguen = jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id = :id")
                .param("id", expediente).query(Integer.class).single();
        assertThat(siguen).isEqualTo(1);
    }

    @Test
    @DisplayName("el historial se lee del cambio mas reciente al mas antiguo")
    void ordenCronologicoInverso() {
        servicio.editar(expediente, new JudicialCaseForm(null, "EXP-HIST-2026", null, null,
                "Primero", null, null, null, null, null, null, null, null, null, 1L), jefa);
        long v = jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(Long.class).single();
        servicio.editar(expediente, new JudicialCaseForm(null, "EXP-HIST-2026", null, null,
                "Segundo", null, null, null, null, null, null, null, null, null, v), jefa);

        var entradas = historial.deEntidad("JUDICIAL_CASE", expediente, Paging.of(0));

        assertThat(entradas).hasSize(2);
        assertThat(entradas.get(0).afterValues()).contains("Segundo");
        assertThat(entradas.get(1).afterValues()).contains("Primero");
    }
}
