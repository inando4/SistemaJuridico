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
import pe.org.beneficencia.legalcontrol.access.RedeemService;
import pe.org.beneficencia.legalcontrol.access.UserAdminService;

/**
 * Ciclo completo de una cuenta, sin ningun servicio externo.
 *
 * <p>El equipo son cinco personas en una oficina: el codigo se entrega en mano y
 * el sistema no envia nada. Esta prueba demuestra que el ciclo cierra sin correo.
 */
class AccountLifecycleIT extends PostgresIntegrationTest {

    private static final String CLAVE = "una frase larga y valida";

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserAdminService cuentas;
    @Autowired private RedeemService canje;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "respaldo@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private String estado(String correo) {
        return jdbc.sql("SELECT status FROM app_user WHERE email = :c")
                .param("c", correo).query(String.class).single();
    }

    @Test
    @DisplayName("alta, canje del codigo y acceso: el ciclo cierra sin correo")
    void cicloCompleto() {
        var alta = cuentas.crear("Abogado Nuevo", "nuevo@ejemplo.test", "LAWYER", jefa);

        assertThat(alta.correcta()).isTrue();
        assertThat(alta.codigo()).isNotBlank();
        assertThat(estado("nuevo@ejemplo.test")).isEqualTo("PENDING_ACTIVATION");

        var problema = canje.canjear("nuevo@ejemplo.test", alta.codigo(), CLAVE, CLAVE, "127.0.0.1");

        assertThat(problema).isEmpty();
        assertThat(estado("nuevo@ejemplo.test")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("un codigo ya usado no sirve una segunda vez")
    void codigoDeUnSoloUso() {
        var alta = cuentas.crear("Abogado", "uso@ejemplo.test", "LAWYER", jefa);
        canje.canjear("uso@ejemplo.test", alta.codigo(), CLAVE, CLAVE, "127.0.0.1");

        var segundo = canje.canjear("uso@ejemplo.test", alta.codigo(),
                "otra frase larga distinta", "otra frase larga distinta", "127.0.0.1");

        assertThat(segundo).isPresent();
        assertThat(segundo.get()).contains("no es válido");
    }

    @Test
    @DisplayName("generar otro codigo invalida el anterior")
    void generarOtroInvalidaElPrevio() {
        var primera = cuentas.crear("Abogado", "otro@ejemplo.test", "LAWYER", jefa);
        UUID cuenta = primera.id();

        // La jefa perdio el papel y genera otro.
        jdbc.sql("UPDATE app_user SET status='ACTIVE', password_hash=:h WHERE id=:id")
                .param("id", cuenta).param("h", encoder.encode(CLAVE)).update();
        String nuevo = cuentas.emitirRestablecimiento(cuenta, jefa);

        assertThat(canje.canjear("otro@ejemplo.test", primera.codigo(), CLAVE, CLAVE, "127.0.0.1"))
                .as("el codigo de activacion previo ya no sirve").isPresent();
        assertThat(canje.canjear("otro@ejemplo.test", nuevo, "frase nueva y suficientemente larga",
                "frase nueva y suficientemente larga", "127.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("desactivar invalida los codigos pendientes")
    void desactivarInvalidaCodigos() {
        var alta = cuentas.crear("Abogado", "desact@ejemplo.test", "LAWYER", jefa);
        cuentas.desactivar(alta.id(), jefa);

        assertThat(canje.canjear("desact@ejemplo.test", alta.codigo(), CLAVE, CLAVE, "127.0.0.1"))
                .as("un codigo de una cuenta desactivada no debe servir").isPresent();
        assertThat(estado("desact@ejemplo.test")).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("reactivar exige contrasena nueva y devuelve la cuenta a activa")
    void reactivacion() {
        var alta = cuentas.crear("Abogado", "react@ejemplo.test", "LAWYER", jefa);
        canje.canjear("react@ejemplo.test", alta.codigo(), CLAVE, CLAVE, "127.0.0.1");
        cuentas.desactivar(alta.id(), jefa);

        String codigo = cuentas.solicitarReactivacion(alta.id(), jefa);
        assertThat(estado("react@ejemplo.test")).isEqualTo("PENDING_REACTIVATION");

        String nueva = "otra frase completamente distinta";
        assertThat(canje.canjear("react@ejemplo.test", codigo, nueva, nueva, "127.0.0.1")).isEmpty();
        assertThat(estado("react@ejemplo.test")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("el codigo nunca queda en el historial ni en claro en la base")
    void codigoFueraDeLaEvidencia() {
        var alta = cuentas.crear("Abogado", "secreto@ejemplo.test", "LAWYER", jefa);
        String plano = alta.codigo().replace("-", "");

        String evidencia = jdbc.sql("""
                SELECT coalesce(string_agg(
                    coalesce(before_values::text,'') || coalesce(after_values::text,''), ' '), '')
                FROM audit_event
                """).query(String.class).single();

        assertThat(evidencia).doesNotContain(plano).doesNotContain(alta.codigo());

        Integer enClaro = jdbc.sql("""
                SELECT count(*) FROM access_token
                WHERE encode(token_hash, 'escape') LIKE :patron
                """).param("patron", "%" + plano + "%").query(Integer.class).single();
        assertThat(enClaro).isZero();
    }
}
