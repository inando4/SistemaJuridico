package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * El coste de la vista no depende del tamaño del equipo (principio IV).
 *
 * <p>La comprobacion que importa es la invariancia. Cuatro consultas por persona
 * con cinco personas son veinte, y en local no se notan; con quince son sesenta y
 * la pantalla se arrastra. Medir cinco y quince y exigir el <b>mismo numero</b> es
 * lo que distingue una consulta agrupada de un N+1 que todavia no duele.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class EquipoQueryBudgetIT extends PostgresIntegrationTest {

    private static final long PRESUPUESTO = 4;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private LocalDate hoy;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        hoy = LocalDate.now();
        UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        DatosSinteticos.sembrarCalendario(jdbc, jefa, hoy.getYear() - 1, hoy.getYear(),
                hoy.getYear() + 1);
        sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
    }

    private List<UUID> crearEquipo(int cuantos) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            ids.add(SesionDePrueba.crearCuenta(jdbc, encoder,
                    "abogado" + UUID.randomUUID() + "@ejemplo.test", "LAWYER"));
        }
        DatosSinteticos.sembrarCargaDesigual(jdbc, ids, hoy);
        return ids;
    }

    private long consultasDeLaVista() {
        return ContadorDeConsultas.contar(() -> {
            try {
                mvc.perform(get("/equipo").session(sesion));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    @DisplayName("con cinco personas cabe en el presupuesto")
    void conCincoPersonas() {
        crearEquipo(5);

        assertThat(consultasDeLaVista()).isLessThanOrEqualTo(PRESUPUESTO);
    }

    @Test
    @DisplayName("con quince cuesta EXACTAMENTE lo mismo que con cinco")
    void elCosteNoCreceConElEquipo() {
        crearEquipo(5);
        long conCinco = consultasDeLaVista();

        crearEquipo(10);
        long conQuince = consultasDeLaVista();

        assertThat(conQuince)
                .as("triplicar el equipo no puede costar ni una consulta mas")
                .isEqualTo(conCinco);
        assertThat(conQuince).isLessThanOrEqualTo(PRESUPUESTO);
    }
}
