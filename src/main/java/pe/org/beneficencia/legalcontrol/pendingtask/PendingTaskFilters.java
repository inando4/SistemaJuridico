package pe.org.beneficencia.legalcontrol.pendingtask;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Filtros y orden del listado de pendientes.
 *
 * <p>{@code linkedTo} filtra por la clase de vinculo, que es propio de esta
 * entidad: los expedientes no cuelgan de nada, los pendientes si.
 *
 * <p>{@code judicialCaseId} y {@code administrativeProcedureId} filtran por un
 * expediente <b>concreto</b>. Existen para que la ficha del expediente no tenga
 * consulta propia: pide su lista por este mismo camino, y su enlace «Ver todos»
 * es literalmente la misma consulta sin acotar. Dos condiciones separadas para el
 * mismo conjunto acaban separandose —la 004 lo vivio— y asi no hay dos.
 */
public record PendingTaskFilters(
        String q,
        UUID ownerId,
        UUID typeId,
        UUID priorityId,
        UUID statusId,
        String linkedTo,
        UUID judicialCaseId,
        UUID administrativeProcedureId,
        String deadlinePresence,
        Boolean overdue,
        String visibility,
        String alerta,
        String sort,
        String direction,
        int page) {

    public static final List<String> ORDENES =
            List.of("scheduledFor", "deadline", "priority", "title",
                    // Lo que queda por hacer delante de lo ya cumplido. Es el orden
                    // de la ficha del expediente, donde ambas cosas conviven.
                    "pendingFirst");
    public static final List<String> DIRECCIONES = List.of("asc", "desc");
    public static final List<String> VINCULOS =
            List.of("any", "judicial", "administrative", "none");
    public static final List<String> PRESENCIAS = List.of("any", "with", "without");
    /**
     * Cuatro conjuntos distintos, no tres etiquetas del mismo:
     *
     * <ul>
     *   <li>{@code active}: lo que queda por hacer (ni archivado ni cumplido).
     *   <li>{@code notArchived}: lo no archivado, <b>cumplidos incluidos</b>. Es lo
     *       que muestra la ficha del expediente: lo cumplido es su historia, lo
     *       retirado se quito a proposito.
     *   <li>{@code inactive}: lo archivado.
     *   <li>{@code all}: todo.
     * </ul>
     */
    public static final List<String> VISIBILIDADES =
            List.of("active", "notArchived", "inactive", "all");

    /**
     * Focos del dashboard. Cada tarjeta enlaza aqui con uno, para que el listado
     * muestre exactamente lo que la tarjeta contaba sin crear seis pantallas.
     */
    public static final List<String> ALERTAS = List.of("cualquiera", "vencidos", "hoy",
            "proximos", "sin-plazo-antiguos", "activos", "cumplidos-del-mes",
            // De la vista de equipo (insumo, seccion 5.2).
            "semana");

    public static PendingTaskFilters porDefecto() {
        return new PendingTaskFilters(null, null, null, null, null, "any", null, null,
                "any", null, "active", "cualquiera", "scheduledFor", "asc", 0);
    }

    /**
     * Los filtros con los que la ficha de un expediente judicial pide su lista.
     *
     * <p>Existe como fabrica y no escrito a mano en cada controlador de ficha para
     * que las dos fichas —y el enlace «Ver todos» que sale de ellas— usen
     * exactamente los mismos tres valores. Escritos dos veces, uno de los dos se
     * queda sin la siguiente correccion.
     */
    public static PendingTaskFilters deExpedienteJudicial(UUID id) {
        return porDefecto().conVinculoJudicial(id).comoFichaDeExpediente();
    }

    /** El equivalente para un procedimiento administrativo. */
    public static PendingTaskFilters deExpedienteAdministrativo(UUID id) {
        return porDefecto().conVinculoAdministrativo(id).comoFichaDeExpediente();
    }

    private PendingTaskFilters conVinculoJudicial(UUID id) {
        return new PendingTaskFilters(q, ownerId, typeId, priorityId, statusId, linkedTo,
                id, null, deadlinePresence, overdue, visibility, alerta, sort, direction, page);
    }

    private PendingTaskFilters conVinculoAdministrativo(UUID id) {
        return new PendingTaskFilters(q, ownerId, typeId, priorityId, statusId, linkedTo,
                null, id, deadlinePresence, overdue, visibility, alerta, sort, direction, page);
    }

    /** Lo no archivado, cumplidos incluidos, con lo que queda por hacer delante. */
    private PendingTaskFilters comoFichaDeExpediente() {
        return new PendingTaskFilters(q, ownerId, typeId, priorityId, statusId, linkedTo,
                judicialCaseId, administrativeProcedureId, deadlinePresence, overdue,
                "notArchived", alerta, "pendingFirst", "asc", 0);
    }

    /** ¿Se esta mirando un expediente concreto? */
    public boolean porExpediente() {
        return judicialCaseId != null || administrativeProcedureId != null;
    }

    public boolean valido() {
        return ORDENES.contains(sort)
                && DIRECCIONES.contains(direction)
                && VINCULOS.contains(linkedTo)
                && PRESENCIAS.contains(deadlinePresence)
                && VISIBILIDADES.contains(visibility)
                && ALERTAS.contains(alerta)
                // Filtrar a la vez por un judicial y por un administrativo es una
                // contradiccion: ningun pendiente cuelga de los dos (insumo, seccion
                // 9), asi que devolveria vacio siempre sin decir por que.
                && !(judicialCaseId != null && administrativeProcedureId != null)
                && page >= 0;
    }

    public String comoQuery(int nuevaPagina) {
        StringBuilder sb = new StringBuilder();
        anadir(sb, "q", q);
        anadir(sb, "ownerId", ownerId);
        anadir(sb, "typeId", typeId);
        anadir(sb, "priorityId", priorityId);
        anadir(sb, "statusId", statusId);
        anadir(sb, "linkedTo", "any".equals(linkedTo) ? null : linkedTo);
        // Uno de los dos portadores del filtro por expediente. El otro es el campo
        // oculto de list.html: por aqui viajan los enlaces de paginar y ordenar,
        // por alli el formulario GET, que descarta todo lo que no sean sus campos.
        anadir(sb, "judicialCaseId", judicialCaseId);
        anadir(sb, "administrativeProcedureId", administrativeProcedureId);
        anadir(sb, "deadlinePresence", "any".equals(deadlinePresence) ? null : deadlinePresence);
        anadir(sb, "overdue", overdue);
        anadir(sb, "visibility", "active".equals(visibility) ? null : visibility);
        anadir(sb, "alerta", "cualquiera".equals(alerta) ? null : alerta);
        anadir(sb, "sort", "scheduledFor".equals(sort) ? null : sort);
        anadir(sb, "direction", "asc".equals(direction) ? null : direction);
        anadir(sb, "page", nuevaPagina == 0 ? null : nuevaPagina);
        return sb.isEmpty() ? "" : "?" + sb;
    }

    private void anadir(StringBuilder sb, String nombre, Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append('&');
        }
        sb.append(nombre).append('=')
          .append(URLEncoder.encode(valor.toString(), StandardCharsets.UTF_8));
    }
}
