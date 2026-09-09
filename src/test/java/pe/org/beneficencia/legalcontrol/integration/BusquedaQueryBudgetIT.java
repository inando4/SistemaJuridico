package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Presupuesto del buscador global (principio IV).
 *
 * <p><b>Lo que de verdad se defiende es la invariante</b>: el numero de consultas no
 * cambia con el numero de coincidencias. Un techo absoluto es una estimacion que se
 * corrige al medirla —en la 005 el plan dijo 6 y la medicion dio 7, sin ninguna
 * redundante—; que trescientos resultados cuesten lo mismo que tres es una propiedad
 * del diseño, y es lo que se rompe el dia que alguien añade un {@code count(*)} por
 * grupo para enseñar el total exacto.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class BusquedaQueryBudgetIT extends PostgresIntegrationTest {

    /** El termino que compartiran los registros abundantes. */
    private static final String COMUN = "concurrencia";

    /** El termino que tendra un solo registro. */
    private static final String ESCASO = "servidumbre";

    private static final int ABUNDANTES = 300;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        if (sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test",
                    "LAWYER");

            for (int i = 0; i < ABUNDANTES; i++) {
                judicial(abogado, "EXP-%04d-2026".formatted(i), "Materia de " + COMUN);
                pendiente(abogado, "Tarea %04d de %s".formatted(i, COMUN));
            }
            judicial(abogado, "EXP-UNICO-2026", "Materia de " + ESCASO);
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM judicial_case").query(Integer.class).single();
        return total == null || total <= ABUNDANTES;
    }

    private void judicial(UUID owner, String numero, String materia) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, subject,
                                           active, created_at, updated_at, version)
                VALUES (:id, :owner, :numero, :materia, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", owner)
                .param("numero", numero).param("materia", materia).param("ahora", ahora).update();
    }

    private void pendiente(UUID owner, String titulo) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :hoy, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", owner).param("titulo", titulo)
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private long costeDe(String ruta) {
        return ContadorDeConsultas.contar(() -> {
            try {
                mvc.perform(get(ruta).session(sesion));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("el coste del buscador no depende de cuantas coincidencias haya")
    void invarianteFrenteAlNumeroDeResultados() {
        long conMuchos = costeDe("/buscar?q=" + COMUN);
        long conUno = costeDe("/buscar?q=" + ESCASO);

        System.out.printf("Buscador: %d consultas con ~%d coincidencias, %d con 1%n",
                conMuchos, ABUNDANTES * 2, conUno);

        assertThat(conMuchos)
                .as("tres consultas fijas: si esto crece con el resultado, alguien "
                        + "añadio un count(*) por grupo o un N+1 por fila")
                .isEqualTo(conUno);
    }

    @Test
    @DisplayName("el buscador se mantiene dentro de su techo de consultas")
    void dentroDelTecho() {
        long coste = costeDe("/buscar?q=" + COMUN);
        System.out.printf("Buscador: %d consultas%n", coste);

        assertThat(coste)
                .as("tres consultas de busqueda mas las de sesion y plantilla comun")
                .isLessThanOrEqualTo(6L);
    }

    @Test
    @DisplayName("pedir mas resultados de un grupo no encarece la pantalla")
    void paginarNoEncarece() {
        long primera = costeDe("/buscar?q=" + COMUN);
        long segunda = costeDe("/buscar?q=" + COMUN + "&pageJ=1&pageP=2");

        assertThat(segunda)
                .as("la paginacion es LIMIT/OFFSET, no consultas adicionales")
                .isEqualTo(primera);
    }
}
