package pe.org.beneficencia.legalcontrol.catalog;

import java.util.List;

/**
 * Describe uno de los catalogos del sistema.
 *
 * <p>Los cinco comparten forma —mismas columnas, mismas operaciones, mismas reglas—
 * y se diferencian solo en tres datos: su tabla, la tabla que los referencia y la de
 * referencia historica. Eso es lo que este registro captura.
 *
 * <p>Los nombres de tabla y columna son constantes de este archivo, nunca entrada
 * del usuario: no hay superficie de inyeccion al componerlos en el SQL.
 *
 * @param clave          identificador en la ruta y en las plantillas
 * @param tabla          tabla del catalogo
 * @param entidadAuditoria tipo de entidad con el que se registra en la evidencia
 * @param usos           todas las tablas que referencian este catalogo. Es una lista
 *                       y no un par porque desde la 006 un catalogo puede tener mas
 *                       de un usuario: los tipos de pendiente los usan
 *                       {@code pending_task} y {@code manual_activity}. Con un solo
 *                       par, la comprobacion previa al borrado miraba una tabla y
 *                       declaraba «no esta en uso» un valor que si lo estaba
 * @param tablaHistorial tabla de referencia historica
 * @param columnaHistorial columna de esa tabla que apunta aqui
 * @param discriminador  valor de {@code catalog_kind} cuando la tabla lo usa, o nulo
 * @param titulo         encabezado de la pantalla
 * @param ejemplos       valores que el insumo enumera, para orientar en el vacio
 */
public record CatalogDefinition(
        String clave,
        String tabla,
        String entidadAuditoria,
        List<UsoDeCatalogo> usos,
        String tablaHistorial,
        String columnaHistorial,
        String discriminador,
        String titulo,
        List<String> ejemplos) {

    /**
     * Una tabla que apunta a este catalogo.
     *
     * @param tabla   tabla que lo referencia
     * @param columna columna de esa tabla que apunta aqui
     */
    public record UsoDeCatalogo(String tabla, String columna) {
    }

    public static final CatalogDefinition ESTADOS_PROCESALES = new CatalogDefinition(
            "estados-procesales", "procedural_status", "PROCEDURAL_STATUS",
            List.of(new UsoDeCatalogo("judicial_case", "procedural_status_id")),
            "case_history_status_reference", "procedural_status_id", null,
            "Estados procesales",
            List.of("Pendiente de actuación", "En trámite", "Concluido", "Archivado", "Ejecución"));

    public static final CatalogDefinition ESTADOS_ADMINISTRATIVOS = new CatalogDefinition(
            "estados-administrativos", "administrative_status", "ADMINISTRATIVE_STATUS",
            List.of(new UsoDeCatalogo("administrative_procedure", "administrative_status_id")),
            "procedure_history_status_reference", "administrative_status_id", null,
            "Estados de procedimientos administrativos",
            List.of("Pendiente de atención", "Pendiente de documentación", "Atendido",
                    "Observado", "Archivado"));

    public static final CatalogDefinition TIPOS_DE_PENDIENTE = new CatalogDefinition(
            "tipos-de-pendiente", "pending_task_type", "PENDING_TASK_TYPE",
            // Dos usos: la 006 anadio la actividad manual, que tambien toma su tipo
            // de aqui. Si faltara el segundo, la jefa podria borrar un tipo que solo
            // usa una actividad manual y la clave foranea lanzaria una traza.
            List.of(new UsoDeCatalogo("pending_task", "pending_task_type_id"),
                    new UsoDeCatalogo("manual_activity", "pending_task_type_id")),
            "pending_task_history_reference", "catalog_id", "PENDING_TASK_TYPE",
            "Tipos de pendiente",
            List.of("Informe legal", "Oficio", "Carta", "Audiencia", "Seguimiento"));

    public static final CatalogDefinition PRIORIDADES = new CatalogDefinition(
            "prioridades", "priority", "PRIORITY",
            List.of(new UsoDeCatalogo("pending_task", "priority_id")),
            "pending_task_history_reference", "catalog_id", "PRIORITY",
            "Prioridades",
            List.of("Alta", "Media", "Baja"));

    public static final CatalogDefinition ESTADOS_DE_PENDIENTE = new CatalogDefinition(
            "estados-de-pendiente", "pending_task_status", "PENDING_TASK_STATUS",
            List.of(new UsoDeCatalogo("pending_task", "pending_task_status_id")),
            "pending_task_history_reference", "catalog_id", "PENDING_TASK_STATUS",
            "Estados de pendiente",
            List.of("Pendiente", "En proceso", "Pendiente de información", "Reprogramado",
                    "Cumplido", "Cancelado"));

    public static final List<CatalogDefinition> TODOS = List.of(
            ESTADOS_PROCESALES, ESTADOS_ADMINISTRATIVOS,
            TIPOS_DE_PENDIENTE, PRIORIDADES, ESTADOS_DE_PENDIENTE);

    public static CatalogDefinition porClave(String clave) {
        return TODOS.stream().filter(c -> c.clave().equals(clave)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("catalogo desconocido: " + clave));
    }
}
