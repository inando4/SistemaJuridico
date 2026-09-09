package pe.org.beneficencia.legalcontrol.search;

import java.util.List;

import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Los resultados de un tipo de registro, y si hay mas de los que caben.
 *
 * <p>No lleva el total exacto de coincidencias <b>a proposito</b>. Contarlo cuesta
 * un {@code count(*)} por grupo —tres consultas mas— y no cambia nada de lo que se
 * hace con el resultado: quien busca abre el que reconoce o afina el termino. El
 * mecanismo es el sondeo que ya usan todos los listados: se piden 26 para paginas de
 * 25, y si vuelven 26 hay mas.
 *
 * @param resultados los de esta pagina, ya recortados al tamaño
 * @param hayMas     si la consulta devolvio el registro de sondeo
 */
public record GrupoDeResultados<T>(List<T> resultados, boolean hayMas) {

    /**
     * Reparte lo que devolvio la consulta con sondeo.
     *
     * <p>La lista llega con hasta {@code size + 1} elementos; el ultimo no se muestra,
     * solo dice que existe.
     */
    public static <T> GrupoDeResultados<T> desdeSondeo(List<T> filas, Paging pagina) {
        boolean hayMas = filas.size() > pagina.size();
        return new GrupoDeResultados<>(
                hayMas ? List.copyOf(filas.subList(0, pagina.size())) : List.copyOf(filas),
                hayMas);
    }

    public boolean vacio() {
        return resultados.isEmpty();
    }

    public int mostrados() {
        return resultados.size();
    }
}
