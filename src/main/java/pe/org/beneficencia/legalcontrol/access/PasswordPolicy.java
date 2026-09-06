package pe.org.beneficencia.legalcontrol.access;

import java.util.Optional;

/**
 * Politica de contrasenas de FR-026: entre 15 y 128 caracteres.
 *
 * <p>Se admiten espacios y pegado a proposito. Una frase larga es mas facil de
 * recordar y mas dificil de adivinar que ocho caracteres con simbolos raros, y
 * prohibir el pegado solo estorba a quien usa un gestor de contrasenas.
 *
 * <p>No se exige mezcla de mayusculas, digitos ni simbolos: con quince
 * caracteres minimos, esas reglas empujan a la gente hacia patrones predecibles
 * sin ganar resistencia real.
 */
public final class PasswordPolicy {

    public static final int MINIMO = 15;
    public static final int MAXIMO = 128;

    private PasswordPolicy() {
    }

    /**
     * @return el motivo del rechazo, o vacio si la contrasena es aceptable
     */
    public static Optional<String> validar(String contrasena, String confirmacion) {
        if (contrasena == null || contrasena.isEmpty()) {
            return Optional.of("La contrasena es obligatoria.");
        }
        if (contrasena.length() < MINIMO) {
            return Optional.of("La contrasena debe tener al menos " + MINIMO + " caracteres.");
        }
        if (contrasena.length() > MAXIMO) {
            return Optional.of("La contrasena no puede superar los " + MAXIMO + " caracteres.");
        }
        if (!contrasena.equals(confirmacion)) {
            return Optional.of("La contrasena y su confirmacion no coinciden.");
        }
        return Optional.empty();
    }

    public static boolean esValida(String contrasena, String confirmacion) {
        return validar(contrasena, confirmacion).isEmpty();
    }
}
