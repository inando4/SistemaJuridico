package pe.org.beneficencia.legalcontrol.shared;

/**
 * Paginacion compartida: 25 registros por pagina (FR-007).
 *
 * <p>Una pagina fuera de rango no es un error: devuelve vacio y el usuario puede
 * volver atras o retirar los filtros. Un 404 ahi solo le haria perder los filtros
 * que acaba de componer.
 *
 * @param page  pagina solicitada, desde cero
 * @param size  registros por pagina
 */
public record Paging(int page, int size) {

    public static final int TAMANO = 25;

    public static Paging of(Integer page) {
        int solicitada = page == null || page < 0 ? 0 : page;
        return new Paging(solicitada, TAMANO);
    }

    public int offset() {
        return page * size;
    }

    /** Pide un registro de mas para saber si hay pagina siguiente sin contar el total. */
    public int limitConSondeo() {
        return size + 1;
    }
}
