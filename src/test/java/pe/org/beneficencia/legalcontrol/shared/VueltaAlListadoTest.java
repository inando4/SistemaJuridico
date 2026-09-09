package pe.org.beneficencia.legalcontrol.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A donde vuelve una accion ejecutada desde una fila.
 *
 * <p>Es una clase sin estado y sin base de datos, asi que se prueba directamente: lo
 * que importa aqui es que ninguna entrada consiga sacar al usuario de la aplicacion.
 */
class VueltaAlListadoTest {

    private static final String BASE = "/pendientes";

    @Test
    @DisplayName("una cadena de filtros nuestra se conserva entera")
    void conservaLosFiltros() {
        assertThat(VueltaAlListado.a(BASE, "?sort=title&page=2"))
                .isEqualTo("redirect:/pendientes?sort=title&page=2");
    }

    @Test
    @DisplayName("sin cadena se vuelve al listado sin filtros")
    void sinCadena() {
        assertThat(VueltaAlListado.a(BASE, null)).isEqualTo("redirect:/pendientes");
        assertThat(VueltaAlListado.a(BASE, "")).isEqualTo("redirect:/pendientes");
        assertThat(VueltaAlListado.a(BASE, "   ")).isEqualTo("redirect:/pendientes");
    }

    @Test
    @DisplayName("una direccion externa NO saca al usuario de la aplicacion")
    void nadaDeRedirectAbierto() {
        // Es la razon de existir de esta clase. Con un parametro que llevara la ruta
        // entera —el clasico volverA=—, cualquiera de estas cadenas seria un redirect
        // abierto: un enlace del correo llevaria a un sitio ajeno con la apariencia de
        // venir del sistema.
        for (String intento : new String[] {
                "https://otro-sitio.example",
                "//otro-sitio.example",
                "?x=1&y=https://otro-sitio.example",
                "/otra/ruta",
                "?volver=/etc/passwd" }) {
            assertThat(VueltaAlListado.a(BASE, intento))
                    .as("intento: %s", intento)
                    .startsWith("redirect:/pendientes")
                    .doesNotContain("otro-sitio.example")
                    .doesNotContain("//");
        }
    }

    @Test
    @DisplayName("una cadena con caracteres que no producimos se descarta en silencio")
    void cadenaRaraSeDescarta() {
        // Perder los filtros molesta; un error en mitad de una accion que SI se
        // ejecuto es peor, porque deja al usuario sin saber si se guardo.
        assertThat(VueltaAlListado.a(BASE, "?q=hola mundo")).isEqualTo("redirect:/pendientes");
        assertThat(VueltaAlListado.a(BASE, "?q=a\nLocation: http://x"))
                .as("un salto de linea no puede colarse en la cabecera")
                .isEqualTo("redirect:/pendientes");
        assertThat(VueltaAlListado.a(BASE, "sin-interrogante=1"))
                .isEqualTo("redirect:/pendientes");
    }

    @Test
    @DisplayName("acepta lo que produce comoQuery: UUID, porcentajes y guiones")
    void aceptaLoQueProducimos() {
        String real = "?ownerId=3f2504e0-4f89-11d3-9a0c-0305e82c3301"
                + "&q=100%25&visibility=notArchived&sort=pendingFirst&page=3";

        assertThat(VueltaAlListado.a(BASE, real))
                .as("si rechazara una cadena legitima, los filtros se perderian siempre")
                .isEqualTo("redirect:/pendientes" + real);
    }
}
