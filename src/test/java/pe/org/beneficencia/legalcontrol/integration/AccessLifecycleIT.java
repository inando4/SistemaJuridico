package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ciclo de vida de la sesion con el reloj bajo control.
 *
 * <p>Comprueba las dos cosas que definen FR-002 y que no se pueden verificar
 * mirando el codigo: que a las 12 h se corta <b>aunque haya actividad continua</b>,
 * y que revocar la cuenta mata la sesion en la peticion siguiente sin borrar nada.
 */
@AutoConfigureMockMvc
@Import(AccessLifecycleIT.RelojControlado.class)
class AccessLifecycleIT extends PostgresIntegrationTest {

    private static final String CONTRASENA = "una frase larga y valida";
    private static final RelojDePrueba RELOJ =
            new RelojDePrueba(Instant.parse("2026-09-07T08:00:00Z"), ZoneId.of("America/Lima"));

    @TestConfiguration
    static class RelojControlado {
        // Nombre distinto del bean de produccion: Spring Boot no permite
        // sobrescribir definiciones, asi que conviven y @Primary decide.
        @Bean @Primary Clock relojDePrueba() {
            return RELOJ;
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private UUID cuenta;

    @BeforeEach
    void cuentaActiva() {
        jdbc.sql("DELETE FROM auth_attempt").update();
        jdbc.sql("DELETE FROM access_token").update();
        jdbc.sql("DELETE FROM app_user").update();

        cuenta = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO app_user (id, name, email, role, status, password_hash,
                                      auth_version, version, created_by, created_at, updated_at)
                VALUES (:id, 'Abogado', 'abogado@ejemplo.test', 'LAWYER', 'ACTIVE', :hash,
                        1, 1, :id, :ahora, :ahora)
                """)
                .param("id", cuenta).param("hash", encoder.encode(CONTRASENA))
                .param("ahora", ahora).update();
    }

    private MockHttpSession entrar() throws Exception {
        return (MockHttpSession) mvc.perform(
                        formLogin("/login").user("email", "abogado@ejemplo.test").password(CONTRASENA))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    @DisplayName("dentro de la jornada la sesion sigue valida")
    void sesionValidaDentroDeLaJornada() throws Exception {
        MockHttpSession sesion = entrar();

        RELOJ.avanzar(Duration.ofHours(6));

        int estado = mvc.perform(get("/judicial-cases").session(sesion))
                .andReturn().getResponse().getStatus();
        assertThat(estado).as("a las 6 h no debe expulsar").isNotEqualTo(302);
    }

    @Test
    @DisplayName("a las 12 horas se corta aunque haya actividad continua")
    void corteAbsolutoPeseAlaActividad() throws Exception {
        MockHttpSession sesion = entrar();

        // Actividad cada dos horas: la inactividad de 4 h nunca llega a cumplirse.
        for (int i = 0; i < 5; i++) {
            RELOJ.avanzar(Duration.ofHours(2));
            String destino = mvc.perform(get("/judicial-cases").session(sesion))
                    .andReturn().getResponse().getRedirectedUrl();
            assertThat(destino).as("a las %d h todavia no debe expulsar", (i + 1) * 2)
                    .isNotEqualTo("/login?expirada");
        }

        // Con esta ya son 12 h exactas desde el ingreso.
        RELOJ.avanzar(Duration.ofHours(2));
        String destino = mvc.perform(get("/judicial-cases").session(sesion))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(destino).as("al cumplirse 12 h desde el ingreso debe exigir entrar de nuevo, "
                        + "aunque haya habido actividad todo el rato")
                .isEqualTo("/login?expirada");
    }

    @Test
    @DisplayName("desactivar la cuenta mata la sesion en la peticion siguiente")
    void revocacionInmediata() throws Exception {
        MockHttpSession sesion = entrar();

        jdbc.sql("UPDATE app_user SET status = 'INACTIVE', auth_version = auth_version + 1 WHERE id = :id")
                .param("id", cuenta).update();

        String destino = mvc.perform(get("/judicial-cases").session(sesion))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(destino).isEqualTo("/login?expirada");
    }

    @Test
    @DisplayName("cambiar la contrasena invalida las sesiones abiertas")
    void cambioDeContrasenaInvalidaSesiones() throws Exception {
        MockHttpSession sesion = entrar();

        jdbc.sql("UPDATE app_user SET auth_version = auth_version + 1 WHERE id = :id")
                .param("id", cuenta).update();

        String destino = mvc.perform(get("/judicial-cases").session(sesion))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(destino).isEqualTo("/login?expirada");
    }
}
