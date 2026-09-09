package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.pendingtask.PendingTask;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskFilters;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskRepository;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Lo que muestra la ficha y lo que muestra su enlace «Ver todos» son el mismo
 * conjunto en el mismo orden (CE-003).
 *
 * <p>Esta es la prueba que detectaria que los dos caminos se han separado. Hoy no
 * pueden: la ficha llama al mismo {@code listar} con los filtros de
 * {@code deExpedienteJudicial}, asi que la igualdad es estructural. Si alguien
 * introduce mas adelante una consulta propia para la ficha —por rapidez, por
 * comodidad—, esto se pone rojo. Es exactamente lo que en la 004 nadie vigilaba
 * cuando una tarjeta del panel empezo a contar pendientes que su listado no
 * mostraba.
 */
class FichaYListadoCoincidenIT extends PostgresIntegrationTest {

    @Autowired private PendingTaskRepository pendientes;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID expediente;
    private UUID procedimiento;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        UUID otra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");

        expediente = judicial(yo, "EXP-COINCIDE-2026");
        procedimiento = administrativo(yo, "ADM-COINCIDE-2026");

        // Mezcla deliberada: dos responsables, cumplidos, archivados, con y sin
        // fecha limite. Si algun caso se tratara distinto en un camino que en otro,
        // aqui se veria.
        pendiente(yo,   "Uno por hacer",     expediente, null, "2026-03-01", true,  false);
        pendiente(otra, "De la otra abogada", expediente, null, "2026-02-01", true,  false);
        pendiente(yo,   "Sin fecha limite",   expediente, null, null,         true,  false);
        pendiente(yo,   "Ya cumplido",        expediente, null, "2026-01-15", true,  true);
        pendiente(yo,   "Archivado",          expediente, null, "2026-01-20", false, false);
        pendiente(yo,   "Del procedimiento",  null, procedimiento, "2026-04-01", true, false);
    }

    private UUID judicial(UUID owner, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", owner).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private UUID administrativo(UUID owner, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", owner).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private void pendiente(UUID owner, String titulo, UUID j, UUID a, String limite,
                           boolean visible, boolean cumplido) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                          administrative_procedure_id, registered_at,
                                          deadline, completed_at, active,
                                          created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :a, :hoy, CAST(:lim AS date), :fin, :vis, :ts, :ts, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", owner).param("t", titulo)
                .param("j", j).param("a", a).param("hoy", LocalDate.now()).param("lim", limite)
                .param("fin", cumplido ? ahora : null).param("vis", visible)
                .param("ts", ahora).update();
    }

    private List<String> titulos(PendingTaskFilters filtros) {
        List<String> nombres = new ArrayList<>();
        for (PendingTask t : pendientes.listar(filtros, Paging.of(0), LocalDate.now())) {
            nombres.add(t.title());
        }
        return nombres;
    }

    @Test
    @DisplayName("ficha judicial y listado filtrado devuelven lo mismo, en el mismo orden")
    void coincidenEnLoJudicial() {
        List<String> laFicha = titulos(PendingTaskFilters.deExpedienteJudicial(expediente));

        // Lo que produce el enlace «Ver todos»: los mismos tres parametros que
        // comoQuery() pone en la URL.
        List<String> elListado = titulos(new PendingTaskFilters(
                null, null, null, null, null, "any", expediente, null, "any", null,
                "notArchived", "cualquiera", "pendingFirst", "asc", 0));

        assertThat(laFicha)
                .as("mismo conjunto y mismo orden: es la misma consulta")
                .containsExactlyElementsOf(elListado);
        assertThat(laFicha)
                .contains("De la otra abogada", "Ya cumplido")
                .doesNotContain("Archivado", "Del procedimiento");
    }

    @Test
    @DisplayName("ficha administrativa y listado filtrado devuelven lo mismo")
    void coincidenEnLoAdministrativo() {
        List<String> laFicha =
                titulos(PendingTaskFilters.deExpedienteAdministrativo(procedimiento));
        List<String> elListado = titulos(new PendingTaskFilters(
                null, null, null, null, null, "any", null, procedimiento, "any", null,
                "notArchived", "cualquiera", "pendingFirst", "asc", 0));

        assertThat(laFicha).containsExactlyElementsOf(elListado);
        assertThat(laFicha).containsExactly("Del procedimiento");
    }

    @Test
    @DisplayName("el enlace que construye la ficha lleva los tres parametros")
    void elEnlaceLlevaLosTresParametros() {
        String query = PendingTaskFilters.deExpedienteJudicial(expediente).comoQuery(0);

        // Sin cualquiera de los tres, «Ver todos» llevaria a un conjunto distinto
        // del que el usuario acaba de ver en la ficha.
        assertThat(query)
                .contains("judicialCaseId=" + expediente)
                .contains("visibility=notArchived")
                .contains("sort=pendingFirst");
    }
}
