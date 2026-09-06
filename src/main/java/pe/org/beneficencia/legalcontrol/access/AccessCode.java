package pe.org.beneficencia.legalcontrol.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Codigo de un solo uso que JEFA entrega en mano.
 *
 * <p>Se muestra una vez y solo se persiste su digest: ni en claro en la base, ni
 * en registros, ni en una URL. Si se pierde, se genera otro y el anterior muere.
 *
 * <p>El alfabeto excluye caracteres que se confunden al dictarlos o teclearlos
 * (0/O, 1/I/L), porque estos codigos se leen en voz alta o se copian a mano.
 * Veinte caracteres sobre 32 simbolos son cien bits: no se adivina.
 */
public final class AccessCode {

    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LONGITUD = 20;
    private static final int TAMANO_GRUPO = 5;
    private static final SecureRandom AZAR = new SecureRandom();

    private AccessCode() {
    }

    /** Genera un codigo legible, agrupado para dictarlo sin errores. */
    public static String generar() {
        StringBuilder codigo = new StringBuilder(LONGITUD + LONGITUD / TAMANO_GRUPO);
        for (int i = 0; i < LONGITUD; i++) {
            if (i > 0 && i % TAMANO_GRUPO == 0) {
                codigo.append('-');
            }
            codigo.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Digest que se guarda. El codigo original no se puede recuperar de aqui. */
    public static byte[] digest(String codigo) {
        try {
            String normalizado = codigo.trim().toUpperCase().replace("-", "");
            return MessageDigest.getInstance("SHA-256")
                    .digest(normalizado.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
