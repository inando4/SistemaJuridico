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
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseForm;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseService;
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.catalog.CatalogService;

/**
 * El catalogo arranca vacio y un estado que se uso no puede desaparecer.
 *
 * <p>Borrar un estado que aparece en el historial dejaria ese historial
 * apuntando a nada: alguien leeria «se cambio el estado» sin poder saber a cual.
 */
class ProceduralStatusIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CatalogService servicio;
    @Autowired private CatalogRepository catalogo;
    @Autowired private JudicialCaseService expedientes;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private UUID crear(String nombre) {
        assertThat(servicio.crear(CatalogDefinition.ESTADOS_PROCESALES, nombre, null, jefa)).isEmpty();
        return catalogo.todos(CatalogDefinition.ESTADOS_PROCESALES).stream()
                .filter(e -> nombre.equals(e.get("name")))
                .map(e -> (UUID) e.get("id")).findFirst().orElseThrow();
    }

    private long version(UUID id) {
        return ((Number) catalogo.porId(CatalogDefinition.ESTADOS_PROCESALES, id).orElseThrow().get("version")).longValue();
    }

    @Test
    @DisplayName("el catalogo arranca vacio y admite los estados del area")
    void catalogoVacioYCreacion() {
        assertThat(catalogo.todos(CatalogDefinition.ESTADOS_PROCESALES)).isEmpty();

        crear("Pendiente de actuación");
        crear("En tramite");
        crear("Concluido");
        crear("Archivado");

        assertThat(catalogo.todos(CatalogDefinition.ESTADOS_PROCESALES)).hasSize(4);
    }

    @Test
    @DisplayName("un nombre repetido se rechaza, aunque cambie la caja o los espacios")
    void nombreDuplicado() {
        crear("Concluido");

        assertThat(servicio.crear(CatalogDefinition.ESTADOS_PROCESALES, "Concluido", null, jefa)).get().asString().contains("Ya existe");
        assertThat(servicio.crear(CatalogDefinition.ESTADOS_PROCESALES, "  concluido  ", null, jefa)).get().asString().contains("Ya existe");
        assertThat(catalogo.todos(CatalogDefinition.ESTADOS_PROCESALES)).hasSize(1);
    }

    @Test
    @DisplayName("un estado sin usar se puede borrar")
    void sinUsoSeBorra() {
        UUID id = crear("Sobrante");
        assertThat(servicio.eliminar(CatalogDefinition.ESTADOS_PROCESALES, id, version(id), jefa)).isEmpty();
        assertThat(catalogo.todos(CatalogDefinition.ESTADOS_PROCESALES)).isEmpty();
    }

    @Test
    @DisplayName("un estado en uso por un expediente no se puede borrar")
    void enUsoNoSeBorra() {
        UUID estado = crear("En tramite");
        expedientes.crear(formCon("EXP-CAT-2026", estado), jefa.id());

        var problema = servicio.eliminar(CatalogDefinition.ESTADOS_PROCESALES, estado, version(estado), jefa);

        assertThat(problema).get().asString().contains("en uso");
        assertThat(catalogo.todos(CatalogDefinition.ESTADOS_PROCESALES)).hasSize(1);
    }

    @Test
    @DisplayName("un estado que se uso y luego se quito tampoco se puede borrar")
    void usoHistoricoProtege() {
        UUID estado = crear("En tramite");
        var alta = expedientes.crear(formCon("EXP-HIST-2026", estado), jefa.id());

        // Se le quita el estado al expediente: ya no esta en uso actual.
        long v = jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", alta.id()).query(Long.class).single();
        expedientes.editar(alta.id(), formCon("EXP-HIST-2026", null).withVersion(v), jefa);

        assertThat(catalogo.enUsoActual(CatalogDefinition.ESTADOS_PROCESALES, estado)).isFalse();
        assertThat(catalogo.enUsoHistorico(CatalogDefinition.ESTADOS_PROCESALES, estado)).as("el historial lo menciona").isTrue();

        assertThat(servicio.eliminar(CatalogDefinition.ESTADOS_PROCESALES, estado, version(estado), jefa))
                .get().asString().contains("historial");
    }

    @Test
    @DisplayName("deshabilitar no toca los expedientes que ya lo usan")
    void deshabilitarNoTocaExpedientes() {
        UUID estado = crear("En tramite");
        var alta = expedientes.crear(formCon("EXP-DESH-2026", estado), jefa.id());

        servicio.cambiarDisponibilidad(CatalogDefinition.ESTADOS_PROCESALES, estado, false, version(estado), jefa);

        UUID sigue = jdbc.sql("SELECT procedural_status_id FROM judicial_case WHERE id = :id")
                .param("id", alta.id()).query(UUID.class).single();
        assertThat(sigue).as("el expediente conserva su estado").isEqualTo(estado);
        assertThat(catalogo.habilitados(CatalogDefinition.ESTADOS_PROCESALES)).as("pero ya no se ofrece").isEmpty();
    }

    private JudicialCaseForm formCon(String numero, UUID estado) {
        return new JudicialCaseForm(null, numero, null, null, null, estado, null, null,
                null, null, null, null, null, null, null);
    }
}
