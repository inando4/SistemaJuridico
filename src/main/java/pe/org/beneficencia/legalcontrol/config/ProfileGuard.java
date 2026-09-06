package pe.org.beneficencia.legalcontrol.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Impide que la aplicacion arranque sin perfil activo.
 *
 * <p>Sin esta comprobacion, un arranque distraido tomaria la configuracion por
 * defecto y podria acabar conectado a produccion. Falla antes de que se inicialice
 * el datasource, con un mensaje que dice exactamente que hacer.
 *
 * <p>El perfil {@code prod} nunca se activa de forma implicita: hay que pedirlo.
 */
public class ProfileGuard implements EnvironmentPostProcessor {

    private static final String MENSAJE = """

            ============================================================
             ARRANQUE DETENIDO: no se indico ningun perfil activo.
            ============================================================

             Esta aplicacion no arranca sin perfil para que un descuido
             no pueda conectarse a la base de datos de produccion.

             Para desarrollo local (PostgreSQL en Docker, localhost):
               ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
               java -jar target/sistema-juridico.jar --spring.profiles.active=local

             Para produccion (requiere todas las variables de entorno):
               java -jar target/sistema-juridico.jar --spring.profiles.active=prod

            ============================================================
            """;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getActiveProfiles().length == 0) {
            throw new IllegalStateException(MENSAJE);
        }
    }
}
