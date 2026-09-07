package pe.org.beneficencia.legalcontrol.pendingtask;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Validacion del formulario de pendiente.
 *
 * <p>Todos los errores a la vez, y <b>nada se trunca</b>: un texto que excede el
 * presupuesto se rechaza con mensaje.
 */
@Component
public class PendingTaskValidator {

    private static final int LARGO_TITULO = 150;
    private static final int LARGO_TEXTO = 10_000;
    private static final int LARGO_DOCUMENTO = 150;

    public Map<String, String> validar(PendingTaskForm form) {
        Map<String, String> errores = new LinkedHashMap<>();

        String titulo = form.title() == null ? "" : form.title().strip();
        if (titulo.isEmpty()) {
            errores.put("title", "El titulo es obligatorio.");
        } else if (titulo.length() > LARGO_TITULO) {
            errores.put("title", "El titulo no puede superar los " + LARGO_TITULO + " caracteres.");
        }

        // Un pendiente cuelga de un expediente o de ninguno, nunca de los dos.
        if (form.vinculoDoble()) {
            errores.put("judicialCaseId", "Un pendiente puede colgar de un expediente judicial "
                    + "o de uno administrativo, no de los dos.");
        }

        fecha(errores, "receivedAt", form.receivedAt());
        fecha(errores, "scheduledFor", form.scheduledFor());
        fecha(errores, "deadline", form.deadline());

        largo(errores, "description", form.description(), LARGO_TEXTO);
        largo(errores, "notes", form.notes(), LARGO_TEXTO);
        largo(errores, "outputDocumentType", form.outputDocumentType(), LARGO_DOCUMENTO);
        largo(errores, "outputDocumentNumber", form.outputDocumentNumber(), LARGO_DOCUMENTO);

        return errores;
    }

    public static LocalDate fechaNormalizada(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void fecha(Map<String, String> errores, String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        try {
            LocalDate.parse(valor.strip());
        } catch (DateTimeParseException e) {
            // Cubre tanto un formato invalido como un 31 de febrero.
            errores.put(campo, "La fecha no es valida.");
        }
    }

    private void largo(Map<String, String> errores, String campo, String valor, int maximo) {
        if (valor != null && valor.length() > maximo) {
            errores.put(campo, "Este texto supera los " + maximo + " caracteres permitidos.");
        }
    }
}
