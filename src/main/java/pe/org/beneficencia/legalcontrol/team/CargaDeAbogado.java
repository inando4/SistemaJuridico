package pe.org.beneficencia.legalcontrol.team;

import java.util.UUID;

/**
 * Lo que una persona tiene encima, contado al mirar.
 *
 * <p><b>No se almacena jamas</b> (principio V). No hay tabla, ni columna, ni cache:
 * un agregado guardado envejece en silencio y acaba contradiciendo al listado al
 * que enlaza.
 *
 * @param sinPlazoAntiguos {@code Integer} y no {@code int} a proposito: {@code null}
 *        significa «no se puede calcular porque el año no esta revisado», que no es
 *        lo mismo que cero. Un cero afirmaria que nadie lleva mucho esperando, y
 *        seria mentira.
 */
public record CargaDeAbogado(
        UUID id, String nombre, String rol,
        int vencidos, int estaSemana, Integer sinPlazoAntiguos, int activos) {

    public boolean esJefa() {
        return "HEAD".equals(rol);
    }

    /**
     * La carga de la semana: lo vencido mas lo que vence en ella.
     *
     * <p>Lo vencido cuenta porque es trabajo que tiene que ocurrir <b>esta</b>
     * semana tanto como lo que vence en ella. Ordenar solo por lo que vence dejaria
     * a quien arrastra quince cosas por debajo de quien tiene dos de manana.
     *
     * <p>Esta cifra se muestra en pantalla: una lista ordenada por un numero
     * invisible obliga a creerse el orden.
     */
    public int cargaDeLaSemana() {
        return vencidos + estaSemana;
    }
}
