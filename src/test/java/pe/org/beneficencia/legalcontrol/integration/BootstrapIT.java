package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import pe.org.beneficencia.legalcontrol.access.AccessCode;
import pe.org.beneficencia.legalcontrol.access.BootstrapCommand;

/**
 * El arranque solo opera con la tabla vacia y no deja el codigo en claro.
 *
 * <p>Es la unica via de crear una cuenta sin sesion previa, asi que tiene que
 * cerrarse sola en cuanto exista la primera: si siguiera funcionando, seria una
 * puerta trasera para crear una JEFA sin que nadie lo autorice.
 */
class BootstrapIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private BootstrapCommand bootstrap;

    @BeforeEach
    void vaciar() {
        jdbc.sql("DELETE FROM case_history_status_reference").update();
        jdbc.sql("DELETE FROM audit_event").update();
        jdbc.sql("DELETE FROM judicial_case").update();
        jdbc.sql("DELETE FROM access_token").update();
        jdbc.sql("DELETE FROM app_user").update();
    }

    @Test
    @DisplayName("con la tabla vacia crea una JEFA pendiente y devuelve su codigo")
    void creaLaPrimeraJefa() {
        String codigo = bootstrap.ejecutar("Jefa del area", "jefa@ejemplo.test");

        assertThat(codigo).isNotBlank();

        String estado = jdbc.sql("SELECT status FROM app_user WHERE email = 'jefa@ejemplo.test'")
                .query(String.class).single();
        String rol = jdbc.sql("SELECT role FROM app_user WHERE email = 'jefa@ejemplo.test'")
                .query(String.class).single();

        assertThat(estado).isEqualTo("PENDING_ACTIVATION");
        assertThat(rol).isEqualTo("HEAD");
    }

    @Test
    @DisplayName("no crea nada si ya existe alguna cuenta")
    void seCierraTrasLaPrimera() {
        bootstrap.ejecutar("Jefa del area", "jefa@ejemplo.test");

        String segundo = bootstrap.ejecutar("Otra jefa", "otra@ejemplo.test");

        assertThat(segundo).as("el arranque no debe funcionar dos veces").isNull();
        Integer cuentas = jdbc.sql("SELECT count(*) FROM app_user").query(Integer.class).single();
        assertThat(cuentas).isEqualTo(1);
    }

    @Test
    @DisplayName("el codigo no queda en claro en la base, solo su digest")
    void codigoNoPersistidoEnClaro() {
        String codigo = bootstrap.ejecutar("Jefa del area", "jefa@ejemplo.test");

        byte[] guardado = jdbc.sql("SELECT token_hash FROM access_token").query(byte[].class).single();

        assertThat(guardado).isEqualTo(AccessCode.digest(codigo));
        assertThat(new String(guardado)).doesNotContain(codigo);

        Integer coincidencias = jdbc.sql("""
                SELECT count(*) FROM access_token
                WHERE encode(token_hash, 'escape') LIKE :patron
                """).param("patron", "%" + codigo.replace("-", "") + "%")
                .query(Integer.class).single();
        assertThat(coincidencias).isZero();
    }

    @Test
    @DisplayName("el codigo generado es legible: sin caracteres que se confundan")
    void codigoLegible() {
        String codigo = bootstrap.ejecutar("Jefa del area", "jefa@ejemplo.test");
        assertThat(codigo.replace("-", "")).doesNotContain("0", "O", "1", "I", "L");
        assertThat(codigo).hasSize(23);   // 20 caracteres y 3 separadores
    }
}
