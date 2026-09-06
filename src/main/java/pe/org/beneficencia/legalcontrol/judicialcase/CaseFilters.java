package pe.org.beneficencia.legalcontrol.judicialcase;

import java.util.List;
import java.util.UUID;

/**
 * Filtros y orden del listado (FR-008).
 *
 * <p>El orden va contra una lista cerrada: cualquier otro valor se rechaza. Es lo
 * que impide que el parametro de ordenacion acabe concatenado en el SQL.
 *
 * <p>Los filtros se conservan al paginar y al volver del detalle, para que nadie
 * tenga que recomponerlos.
 */
public record CaseFilters(
        String q,
        UUID ownerId,
        UUID proceduralStatusId,
        String subject,
        String deadlinePresence,   // any | with | without
        Boolean overdue,
        String visibility,         // active | inactive | all
        String sort,               // caseNumber | owner | deadline
        String direction,          // asc | desc
        int page) {

    public static final List<String> ORDENES = List.of("caseNumber", "owner", "deadline");
    public static final List<String> DIRECCIONES = List.of("asc", "desc");
    public static final List<String> PRESENCIAS = List.of("any", "with", "without");
    public static final List<String> VISIBILIDADES = List.of("active", "inactive", "all");

    /** Predeterminado: todos los responsables, solo activos, numero ascendente. */
    public static CaseFilters porDefecto() {
        return new CaseFilters(null, null, null, null, "any", null, "active",
                "caseNumber", "asc", 0);
    }

    public boolean valido() {
        return ORDENES.contains(sort)
                && DIRECCIONES.contains(direction)
                && PRESENCIAS.contains(deadlinePresence)
                && VISIBILIDADES.contains(visibility)
                && page >= 0;
    }

    /** Cadena de consulta para conservar los filtros al paginar. */
    public String comoQuery(int nuevaPagina) {
        StringBuilder sb = new StringBuilder();
        anadir(sb, "q", q);
        anadir(sb, "ownerId", ownerId);
        anadir(sb, "proceduralStatusId", proceduralStatusId);
        anadir(sb, "subject", subject);
        anadir(sb, "deadlinePresence", "any".equals(deadlinePresence) ? null : deadlinePresence);
        anadir(sb, "overdue", overdue);
        anadir(sb, "visibility", "active".equals(visibility) ? null : visibility);
        anadir(sb, "sort", "caseNumber".equals(sort) ? null : sort);
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
          .append(java.net.URLEncoder.encode(valor.toString(), java.nio.charset.StandardCharsets.UTF_8));
    }
}
