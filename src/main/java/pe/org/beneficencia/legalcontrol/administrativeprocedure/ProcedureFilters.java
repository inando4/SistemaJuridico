package pe.org.beneficencia.legalcontrol.administrativeprocedure;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Filtros y orden del listado administrativo.
 *
 * <p>El orden va contra lista cerrada: es lo que impide que el parametro acabe
 * concatenado en el SQL. Los filtros se conservan al paginar y al volver de la
 * ficha, para que nadie tenga que recomponerlos.
 */
public record ProcedureFilters(
        String q,
        UUID ownerId,
        UUID administrativeStatusId,
        String requestingArea,
        String deadlinePresence,
        Boolean overdue,
        String visibility,
        String sort,
        String direction,
        int page) {

    public static final List<String> ORDENES = List.of("fileNumber", "owner", "deadline");
    public static final List<String> DIRECCIONES = List.of("asc", "desc");
    public static final List<String> PRESENCIAS = List.of("any", "with", "without");
    public static final List<String> VISIBILIDADES = List.of("active", "inactive", "all");

    public static ProcedureFilters porDefecto() {
        return new ProcedureFilters(null, null, null, null, "any", null, "active",
                "fileNumber", "asc", 0);
    }

    public boolean valido() {
        return ORDENES.contains(sort)
                && DIRECCIONES.contains(direction)
                && PRESENCIAS.contains(deadlinePresence)
                && VISIBILIDADES.contains(visibility)
                && page >= 0;
    }

    public String comoQuery(int nuevaPagina) {
        StringBuilder sb = new StringBuilder();
        anadir(sb, "q", q);
        anadir(sb, "ownerId", ownerId);
        anadir(sb, "administrativeStatusId", administrativeStatusId);
        anadir(sb, "requestingArea", requestingArea);
        anadir(sb, "deadlinePresence", "any".equals(deadlinePresence) ? null : deadlinePresence);
        anadir(sb, "overdue", overdue);
        anadir(sb, "visibility", "active".equals(visibility) ? null : visibility);
        anadir(sb, "sort", "fileNumber".equals(sort) ? null : sort);
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
