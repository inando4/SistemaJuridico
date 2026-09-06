package pe.org.beneficencia.legalcontrol.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj que la prueba adelanta a voluntad.
 *
 * <p>Permite comprobar el corte de 12 h sin esperar doce horas, y sin depender
 * del reloj de la maquina que ejecuta las pruebas.
 */
public class RelojDePrueba extends Clock {

    private Instant ahora;
    private final ZoneId zona;

    public RelojDePrueba(Instant inicio, ZoneId zona) {
        this.ahora = inicio;
        this.zona = zona;
    }

    public void avanzar(Duration cuanto) {
        ahora = ahora.plus(cuanto);
    }

    @Override public ZoneId getZone()            { return zona; }
    @Override public Clock withZone(ZoneId otra) { return new RelojDePrueba(ahora, otra); }
    @Override public Instant instant()           { return ahora; }
}
