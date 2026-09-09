package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.shared.OpcionesDeFiltro;

/**
 * Las opciones de los desplegables de filtro: <b>dos consultas</b> por pantalla, y las
 * cuentas desactivadas dentro.
 */
@Import(ContadorDeConsultas.class)
class OpcionesDeFiltroIT extends PostgresIntegrationTest {

    @Autowired private OpcionesDeFiltro opciones;
    @Autowired private CatalogRepository catalogos;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private static final List<CatalogDefinition> LOS_DE_PENDIENTES = List.of(
            CatalogDefinition.TIPOS_DE_PENDIENTE,
            CatalogDefinition.PRIORIDADES,
            CatalogDefinition.ESTADOS_DE_PENDIENTE);

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID activa = SesionDePrueba.crearCuenta(jdbc, encoder, "activa@ejemplo.test", "LAWYER");
        UUID sinPuesto = SesionDePrueba.crearCuenta(jdbc, encoder, "salio@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Abogada Activa' WHERE id = :id")
                .param("id", activa).update();
        jdbc.sql("""
                UPDATE app_user SET name = 'Abogada Que Salio', status = 'INACTIVE'
                WHERE id = :id
                """)
                .param("id", sinPuesto).update();

        // La que salio deja trabajo detras: es lo que hace falta poder encontrar.
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, active,
                                          created_at, updated_at, version)
                VALUES (:id, :o, 'Informe a medio hacer', :hoy, true, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", sinPuesto)
                .param("hoy", LocalDate.now())
                .param("ts", Timestamp.from(Instant.now())).update();

        // Se usa el sembrador comun: los catalogos exigen created_by, y escribir el
        // INSERT a mano aqui seria una tercera copia de ese detalle.
        Timestamp ahora = Timestamp.from(Instant.now());
        DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "Tipo", 2, activa, ahora);
        DatosSinteticos.sembrarCatalogo(jdbc, "priority", "Prioridad", 1, activa, ahora);
        DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "Estado", 1, activa, ahora);
        DatosSinteticos.sembrarCatalogo(jdbc, "procedural_status", "Procesal", 1, activa, ahora);
    }

    @Test
    @DisplayName("una pantalla cuesta dos consultas: catalogos y cuentas (D3)")
    void dosConsultasPorPantalla() {
        long coste = ContadorDeConsultas.contar(() -> {
            Model modelo = new ConcurrentModel();
            opciones.poblar(modelo, LOS_DE_PENDIENTES);
        });
        System.out.printf("Opciones de filtro (3 catalogos): %d consultas%n", coste);

        assertThat(coste)
                .as("una por desplegable serian cuatro; el UNION las junta en una")
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("con un solo catalogo tambien son dos, no una por si acaso")
    void unSoloCatalogo() {
        long coste = ContadorDeConsultas.contar(() -> {
            Model modelo = new ConcurrentModel();
            opciones.poblar(modelo, List.of(CatalogDefinition.ESTADOS_PROCESALES));
        });
        assertThat(coste).isEqualTo(2L);
    }

    @Test
    @DisplayName("habilitadosDeVarios devuelve lo mismo que habilitados uno a uno")
    void elUnionNoSeSeparaDeHabilitados() {
        // Es lo unico que impide que el criterio del UNION y el de habilitados()
        // acaben divergiendo. Si alguien cambia uno y no el otro, un desplegable
        // ofreceria opciones que otro no, sin que nada avisara.
        var deVarios = catalogos.habilitadosDeVarios(LOS_DE_PENDIENTES);

        for (CatalogDefinition c : LOS_DE_PENDIENTES) {
            List<String> porUnion = deVarios.get(c.clave()).stream()
                    .map(f -> String.valueOf(f.get("name"))).toList();
            List<String> unoAUno = catalogos.habilitados(c).stream()
                    .map(f -> String.valueOf(f.get("name"))).toList();

            assertThat(porUnion)
                    .as("catalogo %s: mismas opciones y mismo orden", c.clave())
                    .containsExactlyElementsOf(unoAUno);
        }
    }

    @Test
    @DisplayName("un catalogo deshabilitado desaparece del desplegable")
    void deshabilitadoFuera() {
        jdbc.sql("UPDATE pending_task_type SET enabled = false WHERE name = 'Tipo 2'").update();

        var deVarios = catalogos.habilitadosDeVarios(LOS_DE_PENDIENTES);

        assertThat(deVarios.get(CatalogDefinition.TIPOS_DE_PENDIENTE.clave()).stream()
                        .map(f -> String.valueOf(f.get("name"))).toList())
                .hasSize(1)
                .doesNotContain("Tipo 2");
    }

    @Test
    @DisplayName("un catalogo sin opciones habilitadas devuelve lista vacia, no falta")
    void catalogoVacio() {
        jdbc.sql("UPDATE pending_task_type SET enabled = false").update();

        var deVarios = catalogos.habilitadosDeVarios(LOS_DE_PENDIENTES);

        assertThat(deVarios)
                .as("la plantilla itera sobre la clave: si faltara, reventaria")
                .containsKey(CatalogDefinition.TIPOS_DE_PENDIENTE.clave());
        assertThat(deVarios.get(CatalogDefinition.TIPOS_DE_PENDIENTE.clave())).isEmpty();
    }

    @Test
    @DisplayName("las cuentas desactivadas siguen en el desplegable, marcadas (RF-021, D4)")
    void lasCuentasDesactivadasSiguen() {
        // Con DestinosDeAsignacion.activos esta prueba fallaria, que es exactamente
        // por lo que no se reutiliza: el trabajo de quien dejo el puesto se volveria
        // inencontrable y pareceria de nadie.
        Model modelo = new ConcurrentModel();
        opciones.poblar(modelo, LOS_DE_PENDIENTES);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responsables =
                (List<Map<String, Object>>) modelo.getAttribute("opcionesDeResponsable");

        List<String> nombres = responsables.stream()
                .map(f -> String.valueOf(f.get("name"))).toList();

        assertThat(nombres)
                .contains("Abogada Activa")
                .as("y se dice que esta desactivada, para que se entienda por que sale")
                .contains("Abogada Que Salio (desactivada)");
    }
}
