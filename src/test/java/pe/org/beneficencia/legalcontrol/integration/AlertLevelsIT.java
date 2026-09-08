package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.dashboard.AlertRepository;
import pe.org.beneficencia.legalcontrol.dashboard.NivelDeAlerta;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Los cinco niveles de alerta (insumo, secciones 24 y 35).
 *
 * <p>Lo que mas importa aqui no es que cada tipo salga, sino que un pendiente que
 * encaja en dos <b>no salga dos veces</b>. Es la diferencia entre un CASE evaluado
 * en orden y cinco consultas unidas, y no se nota hasta que alguien tiene un
 * pendiente vencido que ademas estaba programado para hoy.
 */
@AutoConfigureMockMvc
class AlertLevelsIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AlertRepository alertas;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID otro;
    private UUID tipo;
    private UUID prioridad;
    private UUID estado;
    private LocalDate hoy;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        otro = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");

        hoy = LocalDate.now();
        DatosSinteticos.sembrarCalendario(jdbc, yo, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);

        Timestamp ahora = Timestamp.from(Instant.now());
        tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "Informe", 1, yo, ahora)
                .get(0);
        prioridad = DatosSinteticos.sembrarCatalogo(jdbc, "priority", "Alta", 1, yo, ahora).get(0);
        estado = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "Pendiente", 1, yo,
                ahora).get(0);

        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private UUID crear(UUID responsable, String titulo, LocalDate limite,
                       LocalDate programado, LocalDate recepcion, Instant cumplido) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task
                    (id, owner_id, title, pending_task_type_id, priority_id,
                     pending_task_status_id, received_at, registered_at, scheduled_for,
                     deadline, completed_at, active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :tipo, :prioridad, :estado,
                        :recepcion, :recepcion, :programado, :limite, :cumplido,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", responsable).param("titulo", titulo)
                .param("tipo", tipo).param("prioridad", prioridad).param("estado", estado)
                .param("recepcion", recepcion == null ? hoy : recepcion)
                .param("programado", programado).param("limite", limite)
                .param("cumplido", cumplido == null ? null : Timestamp.from(cumplido))
                .param("ahora", ahora)
                .update();
        return id;
    }

    private List<AlertRepository.PendienteConAlerta> misAlertas() {
        return alertas.deResponsable(yo, hoy, hoy.plusDays(5), hoy.minusDays(30), Paging.of(0));
    }

    // ------------------------------------------------------------------ T021

    @Test
    @DisplayName("cada situacion recibe su nivel")
    void cincoNiveles() {
        crear(yo, "Vencido", hoy.minusDays(3), null, null, null);
        crear(yo, "Vence hoy", hoy, null, null, null);
        crear(yo, "Programado hoy", null, hoy, null, null);
        crear(yo, "Vence pronto", hoy.plusDays(2), null, null, null);
        crear(yo, "Antiguo sin plazo", null, null, hoy.minusDays(60), null);

        var porTitulo = misAlertas().stream()
                .collect(java.util.stream.Collectors.toMap(
                        AlertRepository.PendienteConAlerta::titulo,
                        AlertRepository.PendienteConAlerta::nivel));

        assertThat(porTitulo).containsEntry("Vencido", NivelDeAlerta.VENCIDO);
        assertThat(porTitulo).containsEntry("Vence hoy", NivelDeAlerta.VENCE_HOY);
        assertThat(porTitulo).containsEntry("Programado hoy", NivelDeAlerta.PROGRAMADO_HOY);
        assertThat(porTitulo).containsEntry("Vence pronto", NivelDeAlerta.PROXIMO_VENCIMIENTO);
        assertThat(porTitulo).containsEntry("Antiguo sin plazo", NivelDeAlerta.ANTIGUO_SIN_PLAZO);
    }

    @Test
    @DisplayName("las etiquetas en pantalla son las del insumo")
    void etiquetasEnPantalla() throws Exception {
        crear(yo, "Vencido", hoy.minusDays(3), null, null, null);
        crear(yo, "Vence pronto", hoy.plusDays(2), null, null, null);
        crear(yo, "Antiguo sin plazo", null, null, hoy.minusDays(60), null);

        String html = mvc.perform(get("/alertas").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Vencido", "Próximo vencimiento", "Pendiente antiguo");
    }

    @Test
    @DisplayName("un pendiente que no encaja en ningun nivel no aparece")
    void loQueNoUrgeNoSale() {
        crear(yo, "Vence dentro de un mes", hoy.plusDays(30), null, null, null);
        crear(yo, "Sin plazo recien recibido", null, null, hoy.minusDays(1), null);

        assertThat(misAlertas())
                .as("la pantalla es de lo que urge, no de todo")
                .isEmpty();
    }

    @Test
    @DisplayName("un pendiente ajeno no aparece en mis alertas")
    void loAjenoNoSale() {
        crear(otro, "Vencido ajeno", hoy.minusDays(3), null, null, null);

        assertThat(misAlertas()).isEmpty();
    }

    // ------------------------------------------------------------------ T022

    @Test
    @DisplayName("vencido y programado para hoy aparece UNA vez, como vencido")
    void sinDuplicados() {
        crear(yo, "Vencido y de hoy", hoy.minusDays(2), hoy, null, null);

        var filas = misAlertas();

        assertThat(filas)
                .as("cinco consultas unidas lo darian dos veces; un CASE en orden, una")
                .hasSize(1);
        assertThat(filas.get(0).nivel())
                .as("gana el nivel mas urgente de los dos")
                .isEqualTo(NivelDeAlerta.VENCIDO);
    }

    @Test
    @DisplayName("ningun pendiente aparece dos veces, con cualquier combinacion")
    void nuncaHayIdentificadoresRepetidos() {
        crear(yo, "Vencido y de hoy", hoy.minusDays(2), hoy, null, null);
        crear(yo, "Vence hoy y programado hoy", hoy, hoy, null, null);
        crear(yo, "Proximo y programado hoy", hoy.plusDays(2), hoy, null, null);
        crear(yo, "Vencido antiguo", hoy.minusDays(5), null, hoy.minusDays(60), null);

        var ids = misAlertas().stream().map(AlertRepository.PendienteConAlerta::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).hasSize(4);
    }

    // ------------------------------------------------------------------ T023

    @Test
    @DisplayName("los cumplidos no aparecen, aunque su fecha haya pasado")
    void losCumplidosNoSalen() {
        crear(yo, "Cumplido pero vencido", hoy.minusDays(10), null, null, Instant.now());

        assertThat(misAlertas())
                .as("estar hecho pesa mas que la fecha")
                .isEmpty();
    }

    @Test
    @DisplayName("un pendiente archivado tampoco aparece")
    void losArchivadosNoSalen() {
        UUID id = crear(yo, "Archivado y vencido", hoy.minusDays(10), null, null, null);
        jdbc.sql("UPDATE pending_task SET active = false WHERE id = :id")
                .param("id", id).update();

        assertThat(misAlertas()).isEmpty();
    }

    // ------------------------------------------------------------------ T024

    @Test
    @DisplayName("el orden es el de los cinco niveles")
    void ordenPorNivel() {
        // Se crean al reves, para que el orden no venga de la insercion.
        crear(yo, "5 antiguo", null, null, hoy.minusDays(60), null);
        crear(yo, "4 proximo", hoy.plusDays(2), null, null, null);
        crear(yo, "3 programado hoy", null, hoy, null, null);
        crear(yo, "2 vence hoy", hoy, null, null, null);
        crear(yo, "1 vencido", hoy.minusDays(3), null, null, null);

        var titulos = misAlertas().stream()
                .map(AlertRepository.PendienteConAlerta::titulo).toList();

        assertThat(titulos).containsExactly(
                "1 vencido", "2 vence hoy", "3 programado hoy", "4 proximo", "5 antiguo");
    }

    @Test
    @DisplayName("dos aperturas seguidas devuelven el mismo orden")
    void ordenEstable() {
        // Cuatro vencidos con la misma fecha: sin desempate por identificador, el
        // orden entre ellos lo decidiria el motor y podria cambiar entre lecturas.
        for (int i = 1; i <= 4; i++) {
            crear(yo, "Vencido " + i, hoy.minusDays(3), null, null, null);
        }

        var primera = misAlertas().stream().map(AlertRepository.PendienteConAlerta::id).toList();
        var segunda = misAlertas().stream().map(AlertRepository.PendienteConAlerta::id).toList();

        assertThat(segunda).containsExactlyElementsOf(primera);
    }

    @Test
    @DisplayName("sin calendario salen los tres primeros niveles y se avisa")
    void sinCalendarioSalenLosQueNoDependenDeEl() throws Exception {
        crear(yo, "Vencido", hoy.minusDays(3), null, null, null);
        crear(yo, "Vence hoy", hoy, null, null, null);
        crear(yo, "Vence pronto", hoy.plusDays(2), null, null, null);
        crear(yo, "Antiguo sin plazo", null, null, hoy.minusDays(60), null);

        jdbc.sql("DELETE FROM calendar_review").update();

        String html = mvc.perform(get("/alertas").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Faltan días no laborables por revisar");
        assertThat(html)
                .as("lo vencido no depende del calendario: solo compara fechas")
                .contains("Vencido");

        var sinFronteras = alertas.deResponsable(yo, hoy, null, null, Paging.of(0));
        assertThat(sinFronteras.stream().map(AlertRepository.PendienteConAlerta::titulo))
                .containsExactlyInAnyOrder("Vencido", "Vence hoy");
    }
}
