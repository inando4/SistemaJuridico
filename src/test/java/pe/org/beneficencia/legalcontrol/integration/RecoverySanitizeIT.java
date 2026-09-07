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

import pe.org.beneficencia.legalcontrol.access.AccessCodeService;
import pe.org.beneficencia.legalcontrol.access.AuthAttemptService;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.access.RedeemService;
import pe.org.beneficencia.legalcontrol.access.UserAdminService;
import pe.org.beneficencia.legalcontrol.config.RecoverySanitizeCommand;

/**
 * El saneamiento tras restaurar invalida lo que caduca y conserva lo que documenta.
 *
 * <p>Lo que se comprueba de verdad: que un codigo que estaba vivo en el respaldo
 * <b>deja de servir</b> despues, y que la auditoria sale intacta. Sin lo primero,
 * restaurar una copia reabre puertas que alguien ya habia cerrado.
 */
class RecoverySanitizeIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserAdminService cuentas;
    @Autowired private RedeemService canje;
    @Autowired private AuthAttemptService intentos;
    @Autowired private RecoverySanitizeCommand saneamiento;

    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "respaldo@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    @Test
    @DisplayName("un codigo vivo en el respaldo deja de servir tras el saneamiento")
    void codigoRestauradoDejaDeServir() {
        var alta = cuentas.crear("Abogado", "nuevo@ejemplo.test", "LAWYER", jefa);
        String codigo = alta.codigo();

        // Antes de sanear el codigo funcionaria: es justo el peligro.
        saneamiento.limpiar();

        String clave = "una frase larga y valida";
        var resultado = canje.canjear("nuevo@ejemplo.test", codigo, clave, clave, "127.0.0.1");

        assertThat(resultado).as("el codigo restaurado no puede seguir sirviendo").isPresent();
        assertThat(jdbc.sql("SELECT status FROM app_user WHERE email = 'nuevo@ejemplo.test'")
                .query(String.class).single()).isEqualTo("PENDING_ACTIVATION");
    }

    @Test
    @DisplayName("la auditoria queda intacta")
    void auditoriaIntacta() {
        cuentas.crear("Abogado", "auditado@ejemplo.test", "LAWYER", jefa);
        int antes = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(antes).isPositive();

        var resumen = saneamiento.limpiar();

        int despues = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(despues).as("una restauracion no es excusa para perder evidencia")
                .isEqualTo(antes);
        assertThat(resumen.auditoria()).isEqualTo(antes);
    }

    @Test
    @DisplayName("los contadores de abuso se reinician")
    void contadoresReiniciados() {
        for (int i = 0; i < 3; i++) {
            intentos.registrar(AuthAttemptService.Tipo.LOGIN_FAILURE, "alguien@ejemplo.test", "1.2.3.4");
        }
        assertThat(jdbc.sql("SELECT count(*) FROM auth_attempt").query(Integer.class).single())
                .isEqualTo(3);

        saneamiento.limpiar();

        assertThat(jdbc.sql("SELECT count(*) FROM auth_attempt").query(Integer.class).single())
                .isZero();
    }

    @Test
    @DisplayName("usuarios, expedientes y calendario sobreviven")
    void datosDeNegocioSobreviven() {
        java.sql.Timestamp ahora = java.sql.Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-RESTAURADO-2026', true, :ahora, :ahora, 1)
                """).param("id", UUID.randomUUID()).param("owner", jefa.id())
                .param("ahora", ahora).update();

        var resumen = saneamiento.limpiar();

        assertThat(resumen.usuarios()).isEqualTo(2);
        assertThat(resumen.expedientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("tras sanear, la jefa puede emitir un codigo nuevo que si funciona")
    void seEmitenCodigosNuevos() {
        var alta = cuentas.crear("Abogado", "reemitido@ejemplo.test", "LAWYER", jefa);
        saneamiento.limpiar();

        // El flujo autorizado sigue disponible: nadie queda encerrado fuera.
        String nuevo = cuentas.reemitirCodigo(alta.id(), jefa);

        String clave = "otra frase larga y distinta";
        assertThat(canje.canjear("reemitido@ejemplo.test", nuevo, clave, clave, "127.0.0.1"))
                .isEmpty();
    }
}
