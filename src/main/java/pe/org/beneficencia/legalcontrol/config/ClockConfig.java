package pe.org.beneficencia.legalcontrol.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj inyectable, fijado a la hora local de Arequipa.
 *
 * <p>Ningun componente debe llamar a {@code LocalDate.now()} directamente: el
 * calculo de plazos depende de cual es «hoy», y las pruebas necesitan controlarlo
 * sin depender del reloj de la maquina. Con esto, una prueba sustituye el bean y
 * decide la fecha de referencia.
 *
 * <p>Peru tiene una sola zona horaria, pero se nombra explicitamente para que un
 * servidor en otra region no desplace la fecha de vencimiento (FR-014).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
