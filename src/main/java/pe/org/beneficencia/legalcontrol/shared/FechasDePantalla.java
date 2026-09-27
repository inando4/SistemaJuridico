package pe.org.beneficencia.legalcontrol.shared;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import pe.org.beneficencia.legalcontrol.config.ClockConfig;

/** Los instantes se guardan en UTC y se muestran en la zona de trabajo del área. */
@Component("fechas")
public class FechasDePantalla {
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm", Locale.forLanguageTag("es-PE"))
            .withZone(ClockConfig.ZONA);

    public String fechaHora(Instant instante) {
        return instante == null ? "—" : FECHA_HORA.format(instante) + " (hora de Perú)";
    }
}
