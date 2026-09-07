package pe.org.beneficencia.legalcontrol.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Crea cuentas y sesiones para las pruebas de pantalla, sin repetirlo en cada clase. */
public final class SesionDePrueba {

    public static final String CONTRASENA = "una frase larga y valida";

    private SesionDePrueba() {
    }

    public static UUID crearCuenta(JdbcClient jdbc, PasswordEncoder encoder,
                                   String correo, String rol) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO app_user (id, name, email, role, status, password_hash,
                                      auth_version, version, created_by, created_at, updated_at)
                VALUES (:id, :nombre, :correo, :rol, 'ACTIVE', :hash, 1, 1, :id, :ahora, :ahora)
                """)
                .param("id", id).param("nombre", "Usuario " + correo).param("correo", correo)
                .param("rol", rol).param("hash", encoder.encode(CONTRASENA))
                .param("ahora", ahora).update();
        return id;
    }

    public static MockHttpSession entrar(MockMvc mvc, String correo) throws Exception {
        return (MockHttpSession) mvc.perform(
                        formLogin("/login").user("email", correo).password(CONTRASENA))
                .andReturn().getRequest().getSession(false);
    }

    /**
     * Vacia las tablas en orden inverso a sus dependencias.
     *
     * <p>app_user va al final: casi todo lo referencia, incluido el calendario.
     */
    public static void limpiar(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM case_history_status_reference").update();
        jdbc.sql("DELETE FROM procedure_history_status_reference").update();
        jdbc.sql("DELETE FROM pending_task_history_reference").update();
        jdbc.sql("DELETE FROM audit_event").update();
        jdbc.sql("DELETE FROM pending_task").update();
        jdbc.sql("DELETE FROM judicial_case").update();
        jdbc.sql("DELETE FROM administrative_procedure").update();
        jdbc.sql("DELETE FROM procedural_status").update();
        jdbc.sql("DELETE FROM administrative_status").update();
        jdbc.sql("DELETE FROM pending_task_type").update();
        jdbc.sql("DELETE FROM priority").update();
        jdbc.sql("DELETE FROM pending_task_status").update();
        jdbc.sql("DELETE FROM calendar_review").update();
        jdbc.sql("DELETE FROM non_working_day").update();
        jdbc.sql("DELETE FROM calendar_year").update();
        jdbc.sql("DELETE FROM auth_attempt").update();
        jdbc.sql("DELETE FROM access_token").update();
        jdbc.sql("DELETE FROM app_user").update();
    }
}
