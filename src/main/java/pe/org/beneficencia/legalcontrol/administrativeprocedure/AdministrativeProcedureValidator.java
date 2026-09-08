package pe.org.beneficencia.legalcontrol.administrativeprocedure;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Validacion del formulario administrativo.
 *
 * <p>Devuelve todos los errores a la vez para corregirlos de una pasada. Y
 * <b>nada se trunca nunca</b>: un texto que excede el presupuesto se rechaza con
 * mensaje, porque recortar en silencio un dato de un expediente es peor que no
 * guardarlo — nadie se entera de lo que falta.
 */
@Component
public class AdministrativeProcedureValidator {

    private static final int LARGO_TEXTO = 10_000;
    private static final int LARGO_NUMERO = 150;
    private static final int LARGO_AREA = 1_000;

    public Map<String, String> validar(AdministrativeProcedureForm form) {
        Map<String, String> errores = new LinkedHashMap<>();

        String numero = form.fileNumber() == null ? "" : form.fileNumber().strip();
        if (numero.isEmpty()) {
            errores.put("fileNumber", "El número de expediente es obligatorio.");
        } else if (numero.length() > LARGO_NUMERO) {
            errores.put("fileNumber", "El número de expediente no puede superar los "
                    + LARGO_NUMERO + " caracteres.");
        } else if (numero.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            errores.put("fileNumber", "El número de expediente contiene caracteres no válidos.");
        }

        entero(errores, "sequenceNumber", form.sequenceNumber());
        fecha(errores, "receivedAt", form.receivedAt());
        fecha(errores, "deadline", form.deadline());

        largo(errores, "requestingArea", form.requestingArea(), LARGO_AREA);
        largo(errores, "request", form.request(), LARGO_TEXTO);
        largo(errores, "notes", form.notes(), LARGO_TEXTO);

        return errores;
    }

    /**
     * Advertencia de FR-007: la fecha limite es anterior a la de recepcion.
     *
     * <p>Es advertencia y no error: el sistema avisa pero guarda, y no corrige
     * ninguna de las dos fechas. Puede haber un pedido con plazo retroactivo, y
     * decidir cual esta mal no le corresponde al programa.
     *
     * @return el aviso, o vacio si las fechas son coherentes o falta alguna
     */
    public java.util.Optional<String> advertenciaDeFechas(AdministrativeProcedureForm form) {
        LocalDate recepcion = fechaNormalizada(form.receivedAt());
        LocalDate limite = fechaNormalizada(form.deadline());
        if (recepcion == null || limite == null || !limite.isBefore(recepcion)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                "La fecha límite es anterior a la de recepción. Se guardó tal como la escribió; "
                + "revisela si fue un error.");
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

    public static Integer enteroNormalizado(String valor) {
        return valor == null || valor.isBlank() ? null : Integer.valueOf(valor.strip());
    }

    private void entero(Map<String, String> errores, String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        try {
            Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            errores.put(campo, "Debe ser un número entero.");
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
            errores.put(campo, "La fecha no es válida.");
        }
    }

    private void largo(Map<String, String> errores, String campo, String valor, int maximo) {
        if (valor != null && valor.length() > maximo) {
            errores.put(campo, "Este texto supera los " + maximo + " caracteres permitidos.");
        }
    }
}
