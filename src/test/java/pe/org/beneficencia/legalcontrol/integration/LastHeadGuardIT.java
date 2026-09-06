package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.access.UserAdminService;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * El area no puede quedarse sin ninguna jefa activa.
 *
 * <p>Si ocurriera, nadie podria dar de alta cuentas ni generar codigos, y la
 * unica salida seria tocar la base a mano. Por eso no basta con un COUNT previo:
 * dos desactivaciones simultaneas verian ambas que quedan dos jefas.
 */
class LastHeadGuardIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserAdminService cuentas;

    private CuentaActual jefaA;
    private UUID idJefaA;
    private UUID idJefaB;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        idJefaA = SesionDePrueba.crearCuenta(jdbc, encoder, "jefaA@ejemplo.test", "HEAD");
        idJefaB = SesionDePrueba.crearCuenta(jdbc, encoder, "jefaB@ejemplo.test", "HEAD");
        jefaA = new CuentaActual(idJefaA, "Jefa A", "jefaA@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private long jefasActivas() {
        return jdbc.sql("SELECT count(*) FROM app_user WHERE role='HEAD' AND status='ACTIVE'")
                .query(Long.class).single();
    }

    @Test
    @DisplayName("desactivar la ultima jefa activa se rechaza")
    void ultimaJefaProtegida() {
        cuentas.desactivar(idJefaB, jefaA);
        assertThat(jefasActivas()).isEqualTo(1);

        assertThatThrownBy(() -> cuentas.desactivar(idJefaA, jefaA))
                .isInstanceOf(ErrorHandling.SinPermiso.class)
                .hasMessageContaining("ultima cuenta de jefatura");

        assertThat(jefasActivas()).isEqualTo(1);
    }

    @Test
    @DisplayName("una jefa pendiente de activar no cuenta como respaldo")
    void pendienteNoCuenta() {
        cuentas.desactivar(idJefaB, jefaA);
        cuentas.crear("Jefa nueva", "jefaC@ejemplo.test", "HEAD", jefaA);

        // Existe una tercera jefa, pero todavia no puede entrar.
        assertThatThrownBy(() -> cuentas.desactivar(idJefaA, jefaA))
                .isInstanceOf(ErrorHandling.SinPermiso.class);
    }

    @Test
    @DisplayName("dos desactivaciones simultaneas: como maximo prospera una")
    void desactivacionesConcurrentes() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tareas = List.of(
                    () -> intentar(idJefaA),
                    () -> intentar(idJefaB));

            long exitos = pool.invokeAll(tareas).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertThat(exitos).as("no pueden prosperar las dos").isLessThanOrEqualTo(1);
        }
        assertThat(jefasActivas()).as("siempre debe quedar una jefa activa")
                .isGreaterThanOrEqualTo(1);
    }

    private boolean intentar(UUID cuenta) {
        try {
            cuentas.desactivar(cuenta, jefaA);
            return true;
        } catch (Exception rechazada) {
            return false;
        }
    }

    @Test
    @DisplayName("desactivar conserva expedientes e historial de esa persona")
    void desactivarConservaElTrabajo() {
        UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        java.sql.Timestamp ahora = java.sql.Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :owner, 'EXP-CONSERVA-2026', true, :ahora, :ahora, 1)
                """).param("id", UUID.randomUUID()).param("owner", abogado)
                .param("ahora", ahora).update();

        cuentas.desactivar(abogado, jefaA);

        Integer expedientes = jdbc.sql("SELECT count(*) FROM judicial_case WHERE owner_id = :id")
                .param("id", abogado).query(Integer.class).single();
        assertThat(expedientes).as("sus expedientes siguen siendo suyos").isEqualTo(1);
    }
}
