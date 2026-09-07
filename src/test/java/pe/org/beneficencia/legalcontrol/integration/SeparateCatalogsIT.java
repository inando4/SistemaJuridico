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
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.catalog.CatalogService;

/**
 * Los dos catalogos de estados son independientes.
 *
 * <p>«Archivado» existe en ambas listas del insumo. Son valores homonimos de
 * catalogos distintos: no comparten filas ni identificadores, y unificarlos por
 * parecerse seria el error que esta prueba impide.
 */
class SeparateCatalogsIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CatalogService servicio;
    @Autowired private CatalogRepository catalogos;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    @Test
    @DisplayName("«Archivado» puede existir en los dos catalogos sin conflicto")
    void mismoNombreEnAmbos() {
        assertThat(servicio.crear(CatalogDefinition.ESTADOS_PROCESALES, "Archivado", null, jefa)).isEmpty();
        assertThat(servicio.crear(CatalogDefinition.ESTADOS_ADMINISTRATIVOS, "Archivado", null, jefa))
                .as("son catalogos distintos: no colisionan").isEmpty();

        assertThat(catalogos.todos(CatalogDefinition.ESTADOS_PROCESALES)).hasSize(1);
        assertThat(catalogos.todos(CatalogDefinition.ESTADOS_ADMINISTRATIVOS)).hasSize(1);
    }

    @Test
    @DisplayName("ninguno ve las filas del otro")
    void catalogosAislados() {
        servicio.crear(CatalogDefinition.ESTADOS_PROCESALES, "En tramite", null, jefa);
        servicio.crear(CatalogDefinition.ESTADOS_ADMINISTRATIVOS, "Atendido", null, jefa);

        assertThat(catalogos.todos(CatalogDefinition.ESTADOS_PROCESALES))
                .noneMatch(e -> "Atendido".equals(e.get("name")));
        assertThat(catalogos.todos(CatalogDefinition.ESTADOS_ADMINISTRATIVOS))
                .noneMatch(e -> "En tramite".equals(e.get("name")));
    }

    @Test
    @DisplayName("cada catalogo es su propia tabla, no una compartida")
    void cadaCatalogoSuTabla() {
        var tablas = jdbc.sql("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename IN ('procedural_status', 'administrative_status',
                                    'pending_task_type', 'priority', 'pending_task_status')
                ORDER BY tablename
                """).query(String.class).list();

        // Los cinco comparten forma y, tras la fase de unificacion, tambien codigo.
        // Pero siguen siendo tablas distintas: mezclar sus filas seria el error.
        assertThat(tablas).containsExactly("administrative_status", "pending_task_status",
                "pending_task_type", "priority", "procedural_status");
    }
}
