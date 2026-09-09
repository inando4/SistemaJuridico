package pe.org.beneficencia.legalcontrol.activity;

import java.time.LocalDate;
import java.util.List;

import pe.org.beneficencia.legalcontrol.pendingtask.PendingTask;

/**
 * Lo que una persona hizo un dia: lo cumplido mas lo escrito a mano.
 *
 * <p><b>No se persiste jamas</b> (principio V). Se construye al consultar y se
 * descarta. No existe ninguna tabla de «resumen del dia», y no debe crearse: seria
 * una copia de datos que ya estan, que dejaria de coincidir con ellos en cuanto
 * alguien revirtiera un cumplimiento.
 */
public record ActividadDelDia(
        LocalDate dia,
        List<PendingTask> cumplidos,
        List<ManualActivity> manuales) {

    public boolean vacio() {
        return cumplidos.isEmpty() && manuales.isEmpty();
    }

    public int total() {
        return cumplidos.size() + manuales.size();
    }
}
