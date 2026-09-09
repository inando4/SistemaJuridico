package pe.org.beneficencia.legalcontrol.activity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Las reglas de una actividad manual, antes de tocar la base. */
@Component
public class ManualActivityValidator {

    public static final int DESCRIPCION_MAXIMA = 10_000;
    public static final int TIPO_MAXIMO = 150;

    /** @return campo → mensaje; vacio si todo esta bien */
    public Map<String, String> validar(ManualActivityForm form, LocalDate hoy) {
        Map<String, String> errores = new LinkedHashMap<>();

        if (form.description() == null || form.description().isBlank()) {
            errores.put("description", "Escriba qué hizo.");
        } else if (form.description().strip().length() > DESCRIPCION_MAXIMA) {
            errores.put("description", "La descripción es demasiado larga.");
        }

        if (form.performedOn() == null) {
            errores.put("performedOn", "Indique el día.");
        } else if (form.performedOn().isAfter(hoy)) {
            // Es un registro de lo ya hecho. Una fecha futura seria una intencion,
            // y para eso estan los pendientes con fecha programada.
            errores.put("performedOn", "No se puede registrar actividad de un día futuro.");
        }

        // La base tambien lo impide, pero el formulario tiene que poder explicarlo:
        // una excepcion de integridad no le dice nada a quien esta escribiendo.
        if (form.typeId() != null && form.otherType() != null && !form.otherType().isBlank()) {
            errores.put("otherType", "Elija un tipo del catálogo o escriba uno, no las dos cosas.");
        }

        if (form.otherType() != null && form.otherType().strip().length() > TIPO_MAXIMO) {
            errores.put("otherType", "El tipo es demasiado largo.");
        }

        return errores;
    }
}
