package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import pe.org.beneficencia.legalcontrol.administrativestatus.AdministrativeStatusRepository;
import pe.org.beneficencia.legalcontrol.administrativestatus.AdministrativeStatusService;

/**
 * El catalogo arranca vacio y un estado que se uso no puede desaparecer.
 *
 * <p>Borrar un estado que aparece en el historial dejaria ese historial apuntando
 * a nada: alguien leeria «se cambio el estado» sin poder saber a cual.
 */
class AdministrativeStatusIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AdministrativeStatusService servicio;
    @Autowired private AdministrativeStatusRepository catalogo;
    @Autowired private AdministrativeProcedureService procedimientos;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private UUID crear(String nombre) {
        assertThat(servicio.crear(nombre, null, jefa)).isEmpty();
        return catalogo.todos().stream()
                .filter(e -> nombre.equals(e.get("name")))
                .map(e -> (UUID) e.get("id")).findFirst().orElseThrow();
    }

    private long version(UUID id) {
        return ((Number) catalogo.porId(id).orElseThrow().get("version")).longValue();
    }

    private AdministrativeProcedureForm formCon(String numero, UUID estado, Long version) {
        return new AdministrativeProcedureForm(null, numero, null, null, estado,
                null, null, null, true, version);
    }

    @Test
    @DisplayName("el catalogo arranca vacio y admite los estados del area")
    void catalogoVacioYCreacion() {
        assertThat(catalogo.todos()).isEmpty();

        crear("Pendiente de atencion");
        crear("Pendiente de documentacion");
        crear("Atendido");
        crear("Observado");
        crear("Archivado");

        assertThat(catalogo.todos()).hasSize(5);
    }

    @Test
    @DisplayName("un nombre repetido se rechaza, aunque cambie la caja o los espacios")
    void nombreDuplicado() {
        crear("Atendido");

        assertThat(servicio.crear("Atendido", null, jefa)).get().asString().contains("Ya existe");
        assertThat(servicio.crear("  atendido  ", null, jefa)).get().asString().contains("Ya existe");
        assertThat(catalogo.todos()).hasSize(1);
    }

    @Test
    @DisplayName("un estado sin usar se puede borrar")
    void sinUsoSeBorra() {
        UUID id = crear("Sobrante");
        assertThat(servicio.eliminar(id, version(id), jefa)).isEmpty();
        assertThat(catalogo.todos()).isEmpty();
    }

    @Test
    @DisplayName("un estado en uso por un procedimiento no se puede borrar")
    void enUsoNoSeBorra() {
        UUID estado = crear("Atendido");
        procedimientos.crear(formCon("ADM-CAT-2026", estado, null), jefa.id());

        assertThat(servicio.eliminar(estado, version(estado), jefa))
                .get().asString().contains("en uso");
        assertThat(catalogo.todos()).hasSize(1);
    }

    @Test
    @DisplayName("un estado que se uso y luego se quito tampoco se puede borrar")
    void usoHistoricoProtege() {
        UUID estado = crear("Atendido");
        var alta = procedimientos.crear(formCon("ADM-HIST-2026", estado, null), jefa.id());

        long v = jdbc.sql("SELECT version FROM administrative_procedure WHERE id = :id")
                .param("id", alta.id()).query(Long.class).single();
        procedimientos.editar(alta.id(), formCon("ADM-HIST-2026", null, v), jefa);

        assertThat(catalogo.enUsoActual(estado)).isFalse();
        assertThat(catalogo.enUsoHistorico(estado)).as("el historial lo menciona").isTrue();

        assertThat(servicio.eliminar(estado, version(estado), jefa))
                .get().asString().contains("historial");
    }

    @Test
    @DisplayName("deshabilitar no toca los procedimientos que ya lo usan")
    void deshabilitarNoTocaProcedimientos() {
        UUID estado = crear("Atendido");
        var alta = procedimientos.crear(formCon("ADM-DESH-2026", estado, null), jefa.id());

        servicio.cambiarDisponibilidad(estado, false, version(estado), jefa);

        UUID sigue = jdbc.sql("""
                SELECT administrative_status_id FROM administrative_procedure WHERE id = :id
                """).param("id", alta.id()).query(UUID.class).single();
        assertThat(sigue).as("el procedimiento conserva su estado").isEqualTo(estado);
        assertThat(catalogo.habilitados()).as("pero ya no se ofrece").isEmpty();
    }
}
