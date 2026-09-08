package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Solo la jefatura reasigna, y se comprueba <b>en el servidor</b>.
 *
 * <p>Ocultar el formulario en la plantilla no es autorizacion (principio II): la
 * comprobacion tiene que sobrevivir a que alguien envie la peticion a mano, que es
 * como se hace en estas pruebas a proposito.
 */
class ReasignacionPermisosIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private UUID abogadaA;
    private UUID abogadoB;
    private UUID jefaId;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("un abogado no reasigna, ni siquiera un expediente suyo")
    void elAbogadoNoReasignaNiLoPropio() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        assertThatThrownBy(() -> reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0),
                abogadoB, version(ids.get(0)), ReasignacionIT.cuenta(abogadaA, "LAWYER")))
                .isInstanceOf(ErrorHandling.SinPermiso.class);

        assertThat(jdbc.sql("SELECT owner_id FROM judicial_case WHERE id = :id")
                .param("id", ids.get(0)).query(UUID.class).single())
                .as("el rechazo no puede dejar el cambio hecho a medias")
                .isEqualTo(abogadaA);
    }

    @Test
    @DisplayName("sin sesion tampoco se reasigna")
    void sinSesionNoSeReasigna() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        assertThatThrownBy(() -> reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0),
                abogadoB, version(ids.get(0)), null))
                .isInstanceOf(ErrorHandling.SinPermiso.class);
    }

    @Test
    @DisplayName("la jefa si reasigna")
    void laJefaSi() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB,
                version(ids.get(0)), ReasignacionIT.cuenta(jefaId, "HEAD"));

        assertThat(resultado.correcto()).isTrue();
    }
}
