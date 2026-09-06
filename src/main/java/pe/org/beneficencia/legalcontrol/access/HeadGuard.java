package pe.org.beneficencia.legalcontrol.access;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Impide que el equipo se quede sin ninguna jefa activa.
 *
 * <p>Se toma el bloqueo global de {@code access_guard} antes de contar. Un COUNT
 * previo sin bloqueo no basta: dos desactivaciones simultaneas veerian ambas que
 * quedan dos jefas y prosperarian las dos, dejando cero. Con el bloqueo, la
 * segunda cuenta despues de que la primera haya escrito.
 *
 * <p>Una cuenta pendiente de activacion o de reactivacion <b>no cuenta</b>: no
 * puede entrar todavia, asi que dejarla como unica jefa equivaldria a cerrar la
 * puerta y tirar la llave dentro.
 */
@Component
public class HeadGuard {

    private final JdbcClient jdbc;

    public HeadGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Serializa cualquier cambio de estado de cuenta. Debe llamarse dentro de una transaccion. */
    public void tomarBloqueo() {
        jdbc.sql("SELECT id FROM access_guard WHERE id = 1 FOR UPDATE").query().listOfRows();
    }

    /** ¿Desactivar esta cuenta dejaria al area sin jefa activa? */
    public boolean dejariaSinJefa(UUID cuentaADesactivar) {
        Integer jefasActivas = jdbc.sql("""
                SELECT count(*) FROM app_user
                WHERE role = 'HEAD' AND status = 'ACTIVE' AND id <> :excepto
                """).param("excepto", cuentaADesactivar).query(Integer.class).single();
        return jefasActivas == null || jefasActivas == 0;
    }
}
