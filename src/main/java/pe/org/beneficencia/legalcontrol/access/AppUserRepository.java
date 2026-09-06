package pe.org.beneficencia.legalcontrol.access;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a cuentas con SQL explicito. Sin JPA: las consultas se leen tal cual
 * se ejecutan, que es lo que permite razonar sobre el presupuesto de consultas.
 */
@Repository
public class AppUserRepository {

    private final JdbcClient jdbc;

    public AppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca por correo normalizado. Devuelve la cuenta sea cual sea su estado. */
    public Optional<CuentaPersistida> porCorreo(String email) {
        return jdbc.sql("""
                SELECT id, name, email, role, status, password_hash, auth_version
                FROM app_user
                WHERE lower(btrim(email)) = lower(btrim(:email))
                """)
                .param("email", email)
                .query(CuentaPersistida.class)
                .optional();
    }

    /**
     * Revision de autorizacion vigente de una cuenta ACTIVA.
     *
     * <p>Vacio significa que la cuenta ya no puede operar: no existe o dejo de
     * estar activa. Es la comprobacion que corre en cada peticion.
     */
    public Optional<Long> authVersionSiActiva(UUID id) {
        return jdbc.sql("SELECT auth_version FROM app_user WHERE id = :id AND status = 'ACTIVE'")
                .param("id", id)
                .query(Long.class)
                .optional();
    }

    public boolean sinNingunaCuenta() {
        Integer total = jdbc.sql("SELECT count(*) FROM app_user").query(Integer.class).single();
        return total != null && total == 0;
    }

    public record CuentaPersistida(
            UUID id, String name, String email, String role,
            String status, String passwordHash, long authVersion) {

        public boolean activa() {
            return "ACTIVE".equals(status);
        }
    }
}
