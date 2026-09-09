package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

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

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.catalog.CatalogService;

/**
 * Un tipo de pendiente usado <b>solo</b> por una actividad manual esta en uso.
 *
 * <p>Hasta la 006, la comprobacion previa al borrado miraba una sola tabla por
 * catalogo, porque ninguno tenia dos usuarios. La actividad manual es el segundo
 * usuario de los tipos de pendiente, y sin ampliar la comprobacion pasaban dos cosas:
 * el mensaje decia «aparece en el historial» de un valor que esta en uso ahora mismo,
 * y si alguien olvidaba escribir la referencia de catalogo al auditar, el borrado
 * llegaba a la base y la clave foranea lanzaba una traza.
 *
 * <p>Por eso la asercion no es solo «se rechaza», sino <b>con que mensaje</b>.
 */
class CatalogoEnUsoPorActividadIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CatalogService servicio;
    @Autowired private CatalogRepository catalogo;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private UUID crearTipo(String nombre) {
        assertThat(servicio.crear(CatalogDefinition.TIPOS_DE_PENDIENTE, nombre, null, jefa))
                .isEmpty();
        return catalogo.todos(CatalogDefinition.TIPOS_DE_PENDIENTE).stream()
                .filter(e -> nombre.equals(e.get("name")))
                .map(e -> (UUID) e.get("id")).findFirst().orElseThrow();
    }

    private long version(UUID id) {
        return ((Number) catalogo.porId(CatalogDefinition.TIPOS_DE_PENDIENTE, id)
                .orElseThrow().get("version")).longValue();
    }

    /** Una actividad manual insertada directamente: aqui se prueba el catalogo. */
    private void actividadConTipo(UUID tipo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             pending_task_type_id, created_at, updated_at, version)
                VALUES (:id, :owner, :dia, 'Elaboracion de informe legal', :tipo,
                        :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", jefa.id())
                .param("dia", LocalDate.now()).param("tipo", tipo)
                .param("ahora", ahora).update();
    }

    @Test
    @DisplayName("un tipo usado solo por una actividad manual esta en uso")
    void enUsoPorLaActividadManual() {
        UUID tipo = crearTipo("Informe legal");
        actividadConTipo(tipo);

        assertThat(catalogo.enUsoActual(CatalogDefinition.TIPOS_DE_PENDIENTE, tipo))
                .as("ningun pendiente lo usa, pero una actividad manual si")
                .isTrue();
    }

    @Test
    @DisplayName("borrarlo se rechaza diciendo que esta en uso, no que este en el historial")
    void elMensajeEsElCorrecto() {
        UUID tipo = crearTipo("Informe legal");
        actividadConTipo(tipo);

        var rechazo = servicio.eliminar(CatalogDefinition.TIPOS_DE_PENDIENTE, tipo,
                version(tipo), jefa);

        assertThat(rechazo).isPresent();
        assertThat(rechazo.get())
                .as("«en uso» sugiere deshabilitar; «en el historial» seria otro problema")
                .contains("en uso");
    }

    @Test
    @DisplayName("un tipo que nadie usa sigue pudiendose borrar")
    void sinUsoSeBorra() {
        UUID tipo = crearTipo("Tipo sin estrenar");

        assertThat(servicio.eliminar(CatalogDefinition.TIPOS_DE_PENDIENTE, tipo,
                version(tipo), jefa))
                .as("ampliar la comprobacion no puede bloquear lo que si se podia borrar")
                .isEmpty();
    }

    @Test
    @DisplayName("los otros catalogos siguen comportandose igual")
    void losDemasNoCambian() {
        // Los estados procesales solo los usa judicial_case. Con la lista de usos,
        // un catalogo de un solo usuario tiene que seguir funcionando exactamente igual.
        assertThat(CatalogDefinition.ESTADOS_PROCESALES.usos()).hasSize(1);
        assertThat(CatalogDefinition.TIPOS_DE_PENDIENTE.usos()).hasSize(2);
    }
}
