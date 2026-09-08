package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/** A quien NO se le puede pasar trabajo. */
class ReasignacionDestinoInvalidoIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private CuentaActual jefa;
    private UUID jefaId;
    private UUID abogadaA;
    private UUID inactivo;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        inactivo = SesionDePrueba.crearCuenta(jdbc, encoder, "baja@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET status = 'INACTIVE' WHERE id = :id")
                .param("id", inactivo).update();
        jefa = ReasignacionIT.cuenta(jefaId, "HEAD");
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("una cuenta dada de baja no puede recibir trabajo")
    void cuentaInactivaRechazada() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), inactivo,
                version(ids.get(0)), jefa);

        assertThat(resultado.correcto()).isFalse();
        assertThat(resultado.error()).contains("inactiva");
        assertThat(jdbc.sql("SELECT owner_id FROM judicial_case WHERE id = :id")
                .param("id", ids.get(0)).query(UUID.class).single())
                .as("un registro sin nadie que pueda entrar a trabajarlo no sirve")
                .isEqualTo(abogadaA);
    }

    @Test
    @DisplayName("sin destino elegido se pide que se elija")
    void sinDestino() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), null,
                version(ids.get(0)), jefa);

        assertThat(resultado.correcto()).isFalse();
        assertThat(resultado.error()).contains("Elija");
    }

    @Test
    @DisplayName("la jefa si es un destino valido: tambien lleva expedientes")
    void laJefaEsDestinoValido() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), jefaId,
                version(ids.get(0)), jefa);

        assertThat(resultado.correcto()).isTrue();
    }
}
