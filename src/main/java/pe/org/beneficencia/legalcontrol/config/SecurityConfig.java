package pe.org.beneficencia.legalcontrol.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import pe.org.beneficencia.legalcontrol.access.AppUserRepository;
import pe.org.beneficencia.legalcontrol.access.SessionGuardFilter;
import pe.org.beneficencia.legalcontrol.access.SesionIniciada;

/**
 * Seguridad de acceso.
 *
 * <p>La sesion vive en memoria del contenedor: no hay Spring Session ni almacen
 * en base de datos. Guardarla en PostgreSQL costaria un viaje por WAN en cada
 * peticion contra la base gestionada, y eso se come el presupuesto de rendimiento.
 * A cambio, un despliegue cierra las sesiones abiertas, cosa asumida.
 *
 * <p>La inactividad de 4 h la aplica el contenedor por configuracion; el corte
 * absoluto de 12 h y la revocacion inmediata los aplica {@link SessionGuardFilter}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SessionGuardFilter sessionGuard,
                                           AppUserRepository usuarios,
                                           PasswordEncoder encoder,
                                           Clock clock) throws Exception {
        http
            .authorizeHttpRequests(rutas -> rutas
                .requestMatchers("/login", "/access/redeem", "/css/**", "/vendor/**", "/js/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(login -> login
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                // Mensaje unico: no revela si la cuenta existe ni si esta inactiva.
                .failureUrl("/login?error")
                .successHandler(new SesionIniciada(usuarios, clock)))
            .logout(salida -> salida
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?salida")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(sesion -> sesion
                // Sesion nueva al entrar: impide fijacion de sesion.
                .sessionAuthenticationStrategy(new SessionFixationProtectionStrategy()))
            .headers(cabeceras -> cabeceras
                // Ningun codigo de acceso debe escaparse por el Referer.
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            // CSRF activo: toda escritura lo exige.
            .addFilterAfter(sessionGuard, org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        return http.build();
    }
}
