package pe.org.beneficencia.legalcontrol.access;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Alta, desactivacion y reactivacion de cuentas. Exclusivo de JEFA.
 *
 * <p>No hay eliminacion de cuentas: desactivar conserva expedientes e historial
 * con el mismo responsable. Borrar a una persona dejaria huerfano su trabajo y
 * romperia la evidencia de quien hizo que.
 */
@Service
public class UserAdminService {

    private final JdbcClient jdbc;
    private final AccessCodeService codigos;
    private final HeadGuard guard;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public UserAdminService(JdbcClient jdbc, AccessCodeService codigos, HeadGuard guard,
                            AuditRecorder auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.codigos = codigos;
        this.guard = guard;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public record Alta(UUID id, String codigo, String error) {
        public boolean correcta() {
            return error == null;
        }
    }

    @Transactional
    public Alta crear(String nombre, String correo, String rol, CuentaActual jefa) {
        exigirJefa(jefa);
        guard.tomarBloqueo();

        if (!List.of("LAWYER", "HEAD").contains(rol)) {
            return new Alta(null, null, "El rol indicado no es valido.");
        }
        if (correo == null || !correo.contains("@")) {
            return new Alta(null, null, "El correo no es valido.");
        }

        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(clock.instant());
        try {
            jdbc.sql("""
                    INSERT INTO app_user (id, name, email, role, status, auth_version, version,
                                          created_by, created_at, updated_at)
                    VALUES (:id, :nombre, :correo, :rol, 'PENDING_ACTIVATION', 1, 1,
                            :creador, :ahora, :ahora)
                    """)
                    .param("id", id).param("nombre", nombre.strip())
                    .param("correo", correo.strip()).param("rol", rol)
                    .param("creador", jefa.id()).param("ahora", ahora)
                    .update();
        } catch (DuplicateKeyException duplicado) {
            return new Alta(null, null, "Ya existe una cuenta con ese correo.");
        }

        String codigo = codigos.emitir(id, AccessCodeService.Proposito.ACTIVATION, jefa.id(), 1);

        // El codigo NO entra en la evidencia: queda el hecho de que se creo la cuenta.
        auditoria.registrar("APP_USER", id, "CREATE", jefa.id(), id, null,
                Map.of("name", nombre.strip(), "email", correo.strip(),
                       "role", rol, "status", "PENDING_ACTIVATION"), null);

        return new Alta(id, codigo, null);
    }

    @Transactional
    public void desactivar(UUID cuenta, CuentaActual jefa) {
        exigirJefa(jefa);
        guard.tomarBloqueo();

        String estado = estadoDe(cuenta);
        if ("INACTIVE".equals(estado)) {
            return;   // repetir la desactivacion es no-op: sin cambio, sin historial
        }
        if (esJefa(cuenta) && guard.dejariaSinJefa(cuenta)) {
            throw new ErrorHandling.SinPermiso(
                    "No se puede desactivar la ultima cuenta de jefatura activa. "
                    + "Active otra antes.");
        }

        // auth_version sube: las sesiones abiertas mueren en su siguiente peticion.
        jdbc.sql("""
                UPDATE app_user
                SET status = 'INACTIVE', auth_version = auth_version + 1,
                    version = version + 1, updated_at = :ahora
                WHERE id = :id
                """).param("id", cuenta).param("ahora", Timestamp.from(clock.instant())).update();

        codigos.revocarTodos(cuenta);

        auditoria.registrar("APP_USER", cuenta, "DEACTIVATE", jefa.id(), cuenta,
                Map.of("status", estado), Map.of("status", "INACTIVE"), null);
    }

    /** @return el codigo de reactivacion, para mostrarlo una sola vez */
    @Transactional
    public String solicitarReactivacion(UUID cuenta, CuentaActual jefa) {
        exigirJefa(jefa);
        guard.tomarBloqueo();

        String estado = estadoDe(cuenta);
        if (!"INACTIVE".equals(estado)) {
            throw new ErrorHandling.SinPermiso("Solo se reactivan cuentas desactivadas.");
        }

        jdbc.sql("""
                UPDATE app_user SET status = 'PENDING_REACTIVATION',
                                    version = version + 1, updated_at = :ahora
                WHERE id = :id
                """).param("id", cuenta).param("ahora", Timestamp.from(clock.instant())).update();

        String codigo = codigos.emitir(cuenta, AccessCodeService.Proposito.REACTIVATION,
                jefa.id(), authVersionDe(cuenta));

        auditoria.registrar("APP_USER", cuenta, "REACTIVATION_REQUESTED", jefa.id(), cuenta,
                Map.of("status", "INACTIVE"), Map.of("status", "PENDING_REACTIVATION"), null);

        return codigo;
    }

    /** @return el codigo de restablecimiento, para entregarlo en mano */
    @Transactional
    public String emitirRestablecimiento(UUID cuenta, CuentaActual jefa) {
        exigirJefa(jefa);
        if (!"ACTIVE".equals(estadoDe(cuenta))) {
            throw new ErrorHandling.SinPermiso("Solo las cuentas activas admiten restablecimiento.");
        }
        return codigos.emitir(cuenta, AccessCodeService.Proposito.RESET, jefa.id(),
                authVersionDe(cuenta));
    }

    public List<Map<String, Object>> listar() {
        return jdbc.sql("""
                SELECT id, name, email, role, status FROM app_user
                ORDER BY lower(btrim(name))
                """).query().listOfRows();
    }

    private void exigirJefa(CuentaActual actor) {
        if (actor == null || !actor.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra cuentas");
        }
    }

    private String estadoDe(UUID cuenta) {
        return jdbc.sql("SELECT status FROM app_user WHERE id = :id FOR UPDATE")
                .param("id", cuenta).query(String.class).optional()
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("cuenta inexistente"));
    }

    private boolean esJefa(UUID cuenta) {
        return "HEAD".equals(jdbc.sql("SELECT role FROM app_user WHERE id = :id")
                .param("id", cuenta).query(String.class).single());
    }

    private long authVersionDe(UUID cuenta) {
        return jdbc.sql("SELECT auth_version FROM app_user WHERE id = :id")
                .param("id", cuenta).query(Long.class).single();
    }
}
