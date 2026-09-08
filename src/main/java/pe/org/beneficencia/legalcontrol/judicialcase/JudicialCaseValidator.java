package pe.org.beneficencia.legalcontrol.judicialcase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Validacion del formulario de expediente (FR-006).
 *
 * <p>Devuelve todos los errores a la vez, por campo, para que el usuario los
 * corrija de una pasada y no de uno en uno.
 *
 * <p><b>Nada se trunca nunca.</b> Un texto que excede el presupuesto se rechaza
 * con mensaje: recortar en silencio un dato de un expediente es peor que no
 * guardarlo, porque nadie se entera de lo que falta.
 */
@Component
public class JudicialCaseValidator {

    private static final int LARGO_TEXTO = 10_000;
    private static final int LARGO_NUMERO = 150;
    private static final int LARGO_DIRECCION = 1_000;

    public Map<String, String> validar(JudicialCaseForm form) {
        Map<String, String> errores = new LinkedHashMap<>();

        String numero = form.caseNumber() == null ? "" : form.caseNumber().strip();
        if (numero.isEmpty()) {
            errores.put("caseNumber", "El número de expediente es obligatorio.");
        } else if (numero.length() > LARGO_NUMERO) {
            errores.put("caseNumber", "El número de expediente no puede superar los "
                    + LARGO_NUMERO + " caracteres.");
        } else if (numero.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            errores.put("caseNumber", "El número de expediente contiene caracteres no válidos.");
        }

        entero(errores, "sequenceNumber", form.sequenceNumber());
        fecha(errores, "lastActionDate", form.lastActionDate());
        fecha(errores, "deadline", form.deadline());
        monto(errores, form.amount());

        largo(errores, "claimant", form.claimant(), LARGO_TEXTO);
        largo(errores, "respondent", form.respondent(), LARGO_TEXTO);
        largo(errores, "subject", form.subject(), LARGO_TEXTO);
        largo(errores, "lastProceduralAction", form.lastProceduralAction(), LARGO_TEXTO);
        largo(errores, "nextProceduralAction", form.nextProceduralAction(), LARGO_TEXTO);
        largo(errores, "notes", form.notes(), LARGO_TEXTO);
        largo(errores, "managementActions", form.managementActions(), LARGO_TEXTO);
        largo(errores, "propertyAddress", form.propertyAddress(), LARGO_DIRECCION);

        return errores;
    }

    /** Normaliza el monto admitiendo coma o punto decimal, sin redondear en silencio. */
    public static BigDecimal montoNormalizado(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return new BigDecimal(valor.strip().replace(",", "."));
    }

    public static LocalDate fechaNormalizada(String valor) {
        return valor == null || valor.isBlank() ? null : LocalDate.parse(valor.strip());
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

    private void monto(Map<String, String> errores, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        try {
            BigDecimal monto = montoNormalizado(valor);
            if (monto.scale() > 2) {
                errores.put("amount", "El monto admite como máximo dos decimales.");
            } else if (monto.precision() - monto.scale() > 16) {
                errores.put("amount", "El monto es demasiado grande.");
            }
        } catch (NumberFormatException e) {
            errores.put("amount", "El monto debe ser numérico.");
        }
    }

    private void largo(Map<String, String> errores, String campo, String valor, int maximo) {
        if (valor != null && valor.length() > maximo) {
            errores.put(campo, "Este texto supera los " + maximo + " caracteres permitidos.");
        }
    }
}
