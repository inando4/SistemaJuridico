package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4 movio el soporte de pruebas web a su propio modulo y paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Contrato del ingreso.
 *
 * <p>Lo que mas importa aqui no es que se pueda entrar, sino que <b>no se pueda
 * distinguir</b> entre una contrasena equivocada, una cuenta que no existe y una
 * desactivada: las tres deben acabar en el mismo sitio con el mismo mensaje.
 */
@AutoConfigureMockMvc
class LoginContractTest extends PostgresIntegrationTest {

    private static final String CONTRASENA = "una frase larga y valida";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void cuentas() {
        SesionDePrueba.limpiar(jdbc);
        crear("activa@ejemplo.test", "ACTIVE");
        crear("inactiva@ejemplo.test", "INACTIVE");
        crear("pendiente@ejemplo.test", "PENDING_ACTIVATION");
    }

    private void crear(String correo, String estado) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO app_user (id, name, email, role, status, password_hash,
                                      auth_version, version, created_by, created_at, updated_at)
                VALUES (:id, 'Persona', :correo, 'LAWYER', :estado, :hash, 1, 1, :id, :ahora, :ahora)
                """)
                .param("id", id).param("correo", correo).param("estado", estado)
                .param("hash", encoder.encode(CONTRASENA)).param("ahora", ahora).update();
    }

    @Test
    @DisplayName("una cuenta activa con credenciales correctas entra")
    void entraCuentaActiva() throws Exception {
        mvc.perform(formLogin("/login").user("email", "activa@ejemplo.test").password(CONTRASENA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/judicial-cases"));
    }

    @Test
    @DisplayName("la contrasena equivocada, la cuenta inexistente, la inactiva y la pendiente se rechazan igual")
    void rechazosIndistinguibles() throws Exception {
        String[][] casos = {
                {"activa@ejemplo.test", "otra cosa totalmente distinta"},
                {"nadie@ejemplo.test", CONTRASENA},
                {"inactiva@ejemplo.test", CONTRASENA},
                {"pendiente@ejemplo.test", CONTRASENA},
        };
        for (String[] caso : casos) {
            mvc.perform(formLogin("/login").user("email", caso[0]).password(caso[1]))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    @Test
    @DisplayName("al entrar se guarda identidad, rol y el instante del ingreso")
    void guardaLaCuentaEnSesion() throws Exception {
        var resultado = mvc.perform(
                        formLogin("/login").user("email", "activa@ejemplo.test").password(CONTRASENA))
                .andReturn();

        Object guardada = resultado.getRequest().getSession(false)
                .getAttribute(CuentaActual.ATRIBUTO_SESION);

        assertThat(guardada).isInstanceOf(CuentaActual.class);
        CuentaActual cuenta = (CuentaActual) guardada;
        assertThat(cuenta.email()).isEqualTo("activa@ejemplo.test");
        assertThat(cuenta.role()).isEqualTo("LAWYER");
        assertThat(cuenta.esJefa()).isFalse();
        assertThat(cuenta.authenticatedAtEpochSeconds()).isPositive();
    }

    @Test
    @DisplayName("la pantalla de acceso no ofrece registro ni recuperacion publica")
    void sinRegistroNiRecuperacionPublica() throws Exception {
        String html = mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("/access/reset");
        assertThat(html.toLowerCase()).doesNotContain("registrarse", "crear cuenta");
    }
}
