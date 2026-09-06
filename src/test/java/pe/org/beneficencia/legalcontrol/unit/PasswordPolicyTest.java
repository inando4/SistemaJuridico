package pe.org.beneficencia.legalcontrol.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.org.beneficencia.legalcontrol.access.PasswordPolicy;

/** Limites exactos de FR-026, incluidos los bordes. */
class PasswordPolicyTest {

    private static String de(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    @DisplayName("catorce caracteres se rechazan, quince se aceptan")
    void bordeInferior() {
        assertThat(PasswordPolicy.esValida(de(14), de(14))).isFalse();
        assertThat(PasswordPolicy.esValida(de(15), de(15))).isTrue();
    }

    @Test
    @DisplayName("ciento veintiocho se aceptan, ciento veintinueve se rechazan")
    void bordeSuperior() {
        assertThat(PasswordPolicy.esValida(de(128), de(128))).isTrue();
        assertThat(PasswordPolicy.esValida(de(129), de(129))).isFalse();
    }

    @Test
    @DisplayName("se admiten espacios internos y de borde sin recortarlos")
    void admiteEspacios() {
        String conEspacios = "  mi frase larga y valida  ";
        assertThat(conEspacios.length()).isGreaterThanOrEqualTo(PasswordPolicy.MINIMO);
        assertThat(PasswordPolicy.esValida(conEspacios, conEspacios)).isTrue();
    }

    @Test
    @DisplayName("una confirmacion distinta se rechaza aunque ambas sean validas")
    void confirmacionDistinta() {
        assertThat(PasswordPolicy.esValida(de(20), de(21))).isFalse();
        assertThat(PasswordPolicy.validar(de(20), de(21)))
                .get().asString().contains("no coinciden");
    }

    @Test
    @DisplayName("vacia o nula se rechaza sin reventar")
    void vaciaONula() {
        assertThat(PasswordPolicy.esValida(null, null)).isFalse();
        assertThat(PasswordPolicy.esValida("", "")).isFalse();
    }

    @Test
    @DisplayName("el motivo del rechazo no repite la contrasena")
    void noFiltraLaContrasena() {
        String secreta = "una contrasena secretisima";
        assertThat(PasswordPolicy.validar(secreta, "otra cosa distinta"))
                .get().asString().doesNotContain(secreta);
    }
}
