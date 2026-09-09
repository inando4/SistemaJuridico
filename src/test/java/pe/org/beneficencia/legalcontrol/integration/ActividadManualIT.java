package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.activity.ManualActivity;
import pe.org.beneficencia.legalcontrol.activity.ManualActivityForm;
import pe.org.beneficencia.legalcontrol.activity.ManualActivityRepository;
import pe.org.beneficencia.legalcontrol.activity.ManualActivityService;
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.catalog.CatalogService;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * La actividad manual: las tres formas de tipo, el historial y quien puede tocarla.
 *
 * <p>Lo que se comprueba con mas cuidado es que el tipo escrito a mano <b>no</b> entre
 * en el catalogo. Es lo que separa «dar libertad al usuario» de «dejar que cualquiera
 * amplie un catalogo que administra la jefa»: en un mes habria cuarenta tipos y
 * dejaria de servir para nada.
 */
class ActividadManualIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ManualActivityService servicio;
    @Autowired private ManualActivityRepository actividades;
    @Autowired private CatalogService catalogos;
    @Autowired private CatalogRepository catalogo;

    private CuentaActual autor;
    private CuentaActual otro;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID idAutor = SesionDePrueba.crearCuenta(jdbc, encoder, "autor@ejemplo.test", "LAWYER");
        UUID idOtro = SesionDePrueba.crearCuenta(jdbc, encoder, "otro@ejemplo.test", "LAWYER");
        UUID idJefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        long ahora = Instant.now().getEpochSecond();
        autor = new CuentaActual(idAutor, "Autor", "autor@ejemplo.test", "LAWYER", 1, ahora);
        otro = new CuentaActual(idOtro, "Otro", "otro@ejemplo.test", "LAWYER", 1, ahora);
        jefa = new CuentaActual(idJefa, "Jefa", "jefa@ejemplo.test", "HEAD", 1, ahora);
    }

    private UUID tipoDeCatalogo(String nombre) {
        assertThat(catalogos.crear(CatalogDefinition.TIPOS_DE_PENDIENTE, nombre, null, jefa))
                .isEmpty();
        return catalogo.todos(CatalogDefinition.TIPOS_DE_PENDIENTE).stream()
                .filter(e -> nombre.equals(e.get("name")))
                .map(e -> (UUID) e.get("id")).findFirst().orElseThrow();
    }

    private List<Map<String, Object>> eventos(UUID id) {
        return jdbc.sql("""
                SELECT action, before_values::text AS antes, after_values::text AS despues
                FROM audit_event
                WHERE entity_type = 'MANUAL_ACTIVITY' AND entity_id = :id
                ORDER BY occurred_at ASC
                """).param("id", id).query().listOfRows();
    }

    @Test
    @DisplayName("las tres formas de tipo se guardan y se listan")
    void lasTresFormasDeTipo() {
        LocalDate hoy = servicio.hoy();
        UUID tipo = tipoDeCatalogo("Informe legal");

        servicio.crear(new ManualActivityForm("Atencion al publico", hoy, null, null, null), autor);
        servicio.crear(new ManualActivityForm("Informe del mes", hoy, tipo, null, null), autor);
        servicio.crear(new ManualActivityForm("Coordinacion", hoy, null,
                "Reunion con Contabilidad", null), autor);

        List<ManualActivity> delDia = actividades.delDia(autor.id(), hoy);

        assertThat(delDia).hasSize(3);
        assertThat(delDia).extracting(ManualActivity::tipo)
                .as("el LEFT JOIN es lo que deja salir la de sin tipo y la de tipo libre")
                .containsExactly(null, "Informe legal", "Reunion con Contabilidad");
    }

    @Test
    @DisplayName("un tipo escrito a mano NO entra en el catalogo")
    void elTipoLibreNoAmpliaElCatalogo() {
        servicio.crear(new ManualActivityForm("Coordinacion", servicio.hoy(), null,
                "Reunion con Contabilidad", null), autor);

        assertThat(catalogo.todos(CatalogDefinition.TIPOS_DE_PENDIENTE))
                .as("el catalogo lo administra la jefa; una pantalla diaria no lo amplia")
                .noneSatisfy(e -> assertThat(e.get("name")).isEqualTo("Reunion con Contabilidad"));
    }

    @Test
    @DisplayName("elegir tipo del catalogo y escribir uno a la vez se rechaza")
    void tipoAmbiguoSeRechaza() {
        UUID tipo = tipoDeCatalogo("Oficio");
        var form = new ManualActivityForm("Ambiguo", servicio.hoy(), tipo, "Otra cosa", null);

        assertThat(servicio.validar(form))
                .as("la base tambien lo impide, pero el formulario tiene que explicarlo")
                .containsKey("otherType");
    }

    @Test
    @DisplayName("una fecha futura se rechaza")
    void fechaFuturaSeRechaza() {
        var form = new ManualActivityForm("Lo hare manana", servicio.hoy().plusDays(1),
                null, null, null);

        assertThat(servicio.validar(form)).containsKey("performedOn");
    }

    @Test
    @DisplayName("el alta, la correccion y la retirada quedan en el historial")
    void lasTresOperacionesDejanRastro() {
        LocalDate hoy = servicio.hoy();
        UUID id = servicio.crear(
                new ManualActivityForm("Revision de convenio", hoy, null, null, null), autor);

        long v1 = actividades.porId(id).orElseThrow().version();
        servicio.editar(id, new ManualActivityForm("Revision de convenio marco", hoy,
                null, null, v1), v1, autor);

        long v2 = actividades.porId(id).orElseThrow().version();
        servicio.retirar(id, v2, autor);

        var historial = eventos(id);
        assertThat(historial).extracting(e -> e.get("action"))
                .containsExactly("CREATE", "UPDATE", "WITHDRAW");
        assertThat((String) historial.get(1).get("antes"))
                .as("la correccion conserva lo que decia antes")
                .contains("Revision de convenio");
    }

    @Test
    @DisplayName("retirar no borra la fila")
    void retirarNoBorra() {
        LocalDate hoy = servicio.hoy();
        UUID id = servicio.crear(
                new ManualActivityForm("Se va a retirar", hoy, null, null, null), autor);
        long version = actividades.porId(id).orElseThrow().version();

        servicio.retirar(id, version, autor);

        assertThat(actividades.delDia(autor.id(), hoy))
                .as("deja de contarse en el dia")
                .isEmpty();
        assertThat(actividades.porId(id))
                .as("pero la fila sigue, para que el historial pueda explicarla")
                .isPresent();
    }

    @Test
    @DisplayName("solo el autor o la jefa pueden corregir o retirar")
    void soloElAutorOLaJefa() {
        LocalDate hoy = servicio.hoy();
        UUID id = servicio.crear(
                new ManualActivityForm("Parte de trabajo del autor", hoy, null, null, null), autor);
        long version = actividades.porId(id).orElseThrow().version();

        assertThatThrownBy(() -> servicio.retirar(id, version, otro))
                .as("un abogado no toca el parte de trabajo de otro")
                .isInstanceOf(ErrorHandling.SinPermiso.class);

        // La jefa si: es quien responde del area y ya puede corregir cualquier registro.
        servicio.retirar(id, version, jefa);
        assertThat(actividades.delDia(autor.id(), hoy)).isEmpty();
    }

    @Test
    @DisplayName("una version desfasada da conflicto de edicion, no sobrescribe")
    void versionDesfasada() {
        LocalDate hoy = servicio.hoy();
        UUID id = servicio.crear(
                new ManualActivityForm("Original", hoy, null, null, null), autor);
        long v1 = actividades.porId(id).orElseThrow().version();

        servicio.editar(id, new ManualActivityForm("Primera correccion", hoy, null, null, v1),
                v1, autor);

        assertThatThrownBy(() -> servicio.editar(id,
                new ManualActivityForm("Segunda con version vieja", hoy, null, null, v1),
                v1, autor))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);
    }

    @Test
    @DisplayName("el tipo del catalogo queda atado al evento de auditoria")
    void elTipoQuedaReferenciado() {
        UUID tipo = tipoDeCatalogo("Carta");
        servicio.crear(new ManualActivityForm("Carta a Logistica", servicio.hoy(),
                tipo, null, null), autor);

        Integer referencias = jdbc.sql("""
                SELECT count(*) FROM pending_task_history_reference
                WHERE catalog_kind = 'PENDING_TASK_TYPE' AND catalog_id = :tipo
                """).param("tipo", tipo).query(Integer.class).single();

        assertThat(referencias)
                .as("sin esto, enUsoHistorico deja de proteger el valor del catalogo")
                .isEqualTo(1);
    }
}
