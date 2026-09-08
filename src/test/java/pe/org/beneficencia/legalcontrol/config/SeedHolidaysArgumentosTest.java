package pe.org.beneficencia.legalcontrol.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Que anos carga el comando cuando no se le dice ninguno.
 *
 * <p>Es el caso que de verdad se ejecuta: la documentacion enseña el comando sin
 * argumentos, asi que el rango por omision es lo que acaba en la base de datos.
 * No necesita base de datos para comprobarse, y por eso no la usa.
 */
class SeedHolidaysArgumentosTest {

    private final Clock reloj = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"),
            ZoneId.of("America/Lima"));
    private final SeedHolidaysCommand comando =
            new SeedHolidaysCommand(null, null, null, reloj);

    @Test
    @DisplayName("sin argumento carga el ano anterior, el actual y el siguiente")
    void rangoPorOmision() {
        // El anterior porque la antiguedad se mide hacia atras y cruza el cambio de
        // ano; el siguiente porque en diciembre ya se fijan plazos que caen en enero.
        assertThat(comando.anosPedidos(null)).containsExactly(2025, 2026, 2027);
        assertThat(comando.anosPedidos(List.of())).containsExactly(2025, 2026, 2027);
    }

    @Test
    @DisplayName("una lista de anos se respeta tal cual")
    void anosExplicitos() {
        assertThat(comando.anosPedidos(List.of("2027,2028"))).containsExactly(2027, 2028);
        assertThat(comando.anosPedidos(List.of(" 2030 , 2031 "))).containsExactly(2030, 2031);
        assertThat(comando.anosPedidos(List.of("2029"))).containsExactly(2029);
    }
}
