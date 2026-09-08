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
 */
public record PendingTaskFilters(
        String q,
        UUID ownerId,
        UUID typeId,
        UUID priorityId,
        UUID statusId,
        String linkedTo,
        String deadlinePresence,
        Boolean overdue,
        String visibility,
        String alerta,
        String sort,
        String direction,
        int page) {

    public static final List<String> ORDENES =
            List.of("scheduledFor", "deadline", "priority", "title");
    public static final List<String> DIRECCIONES = List.of("asc", "desc");
    public static final List<String> VINCULOS =
            List.of("any", "judicial", "administrative", "none");
    public static final List<String> PRESENCIAS = List.of("any", "with", "without");
    public static final List<String> VISIBILIDADES = List.of("active", "inactive", "all");

    /**
     * Focos del dashboard. Cada tarjeta enlaza aqui con uno, para que el listado
     * muestre exactamente lo que la tarjeta contaba sin crear seis pantallas.
     */
    public static final List<String> ALERTAS = List.of("cualquiera", "vencidos", "hoy",
            "proximos", "sin-plazo-antiguos", "activos", "cumplidos-del-mes",
            // De la vista de equipo (insumo, seccion 5.2).
            "semana");

    public static PendingTaskFilters porDefecto() {
        return new PendingTaskFilters(null, null, null, null, null, "any", "any", null,
                "active", "cualquiera", "scheduledFor", "asc", 0);
    }

    public boolean valido() {
        return ORDENES.contains(sort)
                && DIRECCIONES.contains(direction)
                && VINCULOS.contains(linkedTo)
                && PRESENCIAS.contains(deadlinePresence)
                && VISIBILIDADES.contains(visibility)
                && ALERTAS.contains(alerta)
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
