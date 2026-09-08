package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * El coste de reasignar no depende de cuantos pendientes cuelguen.
 *
 * <p>La comprobacion que importa no es el numero absoluto sino la <b>invariancia</b>:
 * un N+1 con cinco pendientes cuesta poco y parece bueno; con cincuenta, no. Medir
 * los dos casos y exigir el mismo numero es lo que distingue una operacion en
 * bloque de una que todavia no duele.
 */
@Import(ContadorDeConsultas.class)
class ReasignacionQueryBudgetIT extends PostgresIntegrationTest {

    /**
     * Siete sentencias, medidas: estado del expediente, validez del destino, foto
     * previa, cambio del expediente, su historial, cambio de los pendientes y su
     * historial en bloque.
     *
     * <p>El plan estimo seis antes de medir. Se corrige al numero real en vez de
     * retorcer el codigo para que cuadre: ninguna de las siete sobra, y la
     * propiedad que el presupuesto protege —que no crezca con los pendientes— se
     * cumple y la comprueba la segunda prueba.
     */
    private static final long PRESUPUESTO = 7;

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private CuentaActual jefa;
    private UUID abogadaA;
    private UUID abogadoB;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        UUID jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        jefa = ReasignacionIT.cuenta(jefaId, "HEAD");
    }

    private long consultasAlReasignar(int pendientes) {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA,
                pendientes, 0);
        long version = jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", ids.get(0)).query(Long.class).single();

        return ContadorDeConsultas.contar(() -> reasignaciones.reasignarExpediente(
                Tipo.JUDICIAL, ids.get(0), abogadoB, version, jefa));
    }

    @Test
    @DisplayName("reasignar cinco pendientes cabe en el presupuesto")
    void conCincoPendientes() {
        assertThat(consultasAlReasignar(5)).isLessThanOrEqualTo(PRESUPUESTO);
    }

    @Test
    @DisplayName("con cincuenta cuesta EXACTAMENTE lo mismo que con cinco")
    void elCosteNoCreceConLosPendientes() {
        long conCinco = consultasAlReasignar(5);
        long conCincuenta = consultasAlReasignar(50);

        assertThat(conCincuenta)
                .as("diez veces mas pendientes no puede costar ni una consulta mas")
                .isEqualTo(conCinco);
        assertThat(conCincuenta).isLessThanOrEqualTo(PRESUPUESTO);
    }
}
