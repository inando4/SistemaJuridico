package pe.org.beneficencia.legalcontrol.pendingtask;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.shared.BusquedaDeTexto;
import pe.org.beneficencia.legalcontrol.shared.Paging;
import pe.org.beneficencia.legalcontrol.config.ClockConfig;

/**
 * Consultas de pendientes con SQL explicito.
 *
 * <p>El listado se resuelve en <b>una sola consulta</b> con joins al responsable,
 * a los tres catalogos y a los dos expedientes posibles. Son seis joins, pero una
 * sola ida y vuelta: con la base en otra region, eso es lo que decide si la
 * pantalla abre en un segundo o en veinte.
 */
@Repository
public class PendingTaskRepository {

    private static final String SELECCION = """
            SELECT t.id, t.owner_id, u.name AS owner_name,
                   t.title, t.description,
                   t.pending_task_type_id, tipo.name  AS pending_task_type_name,
                   t.priority_id,          prio.name  AS priority_name,
                   t.pending_task_status_id, est.name AS pending_task_status_name,
                   t.judicial_case_id,            jc.case_number  AS judicial_case_number,
                   t.administrative_procedure_id, ap.file_number  AS administrative_procedure_number,
                   t.received_at, t.registered_at, t.scheduled_for, t.deadline, t.completed_at,
                   t.output_document_type, t.output_document_number,
                   t.notes, t.active, t.created_at, t.updated_at, t.version
            FROM pending_task t
            JOIN app_user u ON u.id = t.owner_id
            LEFT JOIN pending_task_type   tipo ON tipo.id = t.pending_task_type_id
            LEFT JOIN priority            prio ON prio.id = t.priority_id
            LEFT JOIN pending_task_status est  ON est.id  = t.pending_task_status_id
            LEFT JOIN judicial_case            jc ON jc.id = t.judicial_case_id
            LEFT JOIN administrative_procedure ap ON ap.id = t.administrative_procedure_id
            """;

    private final JdbcClient jdbc;

    public PendingTaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PendingTask> porId(UUID id) {
        return jdbc.sql(SELECCION + " WHERE t.id = :id")
                .param("id", id).query(PendingTask.class).optional();
    }

    /** Devuelve hasta {@code size + 1} filas: la de mas dice si hay pagina siguiente. */
    public List<PendingTask> listar(PendingTaskFilters filtros, Paging pagina, LocalDate hoy) {
        return listar(filtros, pagina, hoy, null, null);
    }

    /**
     * Variante que acepta las fronteras de dias habiles, para el filtro por foco
     * del dashboard.
     *
     * <p>Las fronteras llegan calculadas desde fuera y son <b>las mismas</b> que
     * uso la tarjeta que enlaza aqui. Si este metodo las recalculara podrian
     * discrepar —por un limite inclusivo frente a uno exclusivo, o por una
     * peticion que cruza la medianoche— y la tarjeta contaria pendientes que su
     * propio listado no muestra.
     */
    public List<PendingTask> listar(PendingTaskFilters filtros, Paging pagina, LocalDate hoy,
                                    LocalDate frontera3, LocalDate hace15) {
        List<String> condiciones = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        switch (filtros.visibility()) {
            // «Activo» es lo que queda por hacer: ni archivado ni ya cumplido. Sin
            // la segunda condicion, lo cumplido seguiria en la lista de trabajo y
            // marcarlo no serviria de nada (historia 2, criterio 1). Lo cumplido
            // tiene su propia pantalla en /cumplidos.
            case "active"   -> condiciones.add("t.active = true AND t.completed_at IS NULL");
            case "inactive" -> condiciones.add("t.active = false");
            default         -> { }
        }
        if (filtros.q() != null && !filtros.q().isBlank()) {
            condiciones.add(CONDICION_TEXTO);
            params.put("q", BusquedaDeTexto.comodin(filtros.q()));
        }
        anadirIgual(condiciones, params, "t.owner_id", "ownerId", filtros.ownerId());
        anadirIgual(condiciones, params, "t.pending_task_type_id", "typeId", filtros.typeId());
        anadirIgual(condiciones, params, "t.priority_id", "priorityId", filtros.priorityId());
        anadirIgual(condiciones, params, "t.pending_task_status_id", "statusId", filtros.statusId());

        switch (filtros.linkedTo()) {
            case "judicial"       -> condiciones.add("t.judicial_case_id IS NOT NULL");
            case "administrative" -> condiciones.add("t.administrative_procedure_id IS NOT NULL");
            case "none"           -> condiciones.add(
                    "t.judicial_case_id IS NULL AND t.administrative_procedure_id IS NULL");
            default               -> { }
        }
        switch (filtros.deadlinePresence()) {
            case "with"    -> condiciones.add("t.deadline IS NOT NULL");
            case "without" -> condiciones.add("t.deadline IS NULL");
            default        -> { }
        }
        if (Boolean.TRUE.equals(filtros.overdue())) {
            condiciones.add("t.deadline IS NOT NULL AND t.deadline < :hoy");
            params.put("hoy", hoy);
        }

        // Foco del dashboard: cada tarjeta enlaza con uno de estos, y la condicion
        // es la misma que la de su cuenta para que ambas coincidan (SC-004).
        String activo = "(t.active AND t.completed_at IS NULL)";
        switch (filtros.alerta()) {
            case "vencidos" -> {
                condiciones.add(activo + " AND t.deadline < :hoy");
                params.put("hoy", hoy);
            }
            case "hoy" -> {
                condiciones.add(activo + " AND (t.deadline = :hoy OR t.scheduled_for = :hoy)");
                params.put("hoy", hoy);
            }
            case "proximos" -> {
                condiciones.add(activo + " AND t.deadline > :hoy"
                        + " AND t.deadline <= CAST(:frontera3 AS date)");
                params.put("hoy", hoy);
                params.put("frontera3", frontera3);
            }
            case "sin-plazo-antiguos" -> {
                condiciones.add(activo + " AND t.deadline IS NULL"
                        + " AND t.received_at < CAST(:hace15 AS date)");
                params.put("hace15", hace15);
            }
            // La carga de la semana de la vista de equipo. La condicion es la
            // misma que la de su recuento para que la lista y el numero coincidan;
            // el desajuste entre tarjeta y listado fue lo que hubo que corregir en
            // la 004.
            case "semana" -> {
                condiciones.add(activo + " AND ("
                        + "(t.deadline      BETWEEN :lunes AND :domingo) OR "
                        + "(t.scheduled_for BETWEEN :lunes AND :domingo))");
                params.put("lunes", pe.org.beneficencia.legalcontrol.team.SemanaDeTrabajo
                        .lunesDe(hoy));
                params.put("domingo", pe.org.beneficencia.legalcontrol.team.SemanaDeTrabajo
                        .domingoDe(hoy));
            }
            case "activos" -> condiciones.add(activo);
            case "cumplidos-del-mes" -> {
                condiciones.add("t.completed_at >= CAST(:inicioDeMes AS date)");
                params.put("inicioDeMes", hoy.withDayOfMonth(1));
            }
            default -> { }
        }

        String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
        String sql = SELECCION + where + orden(filtros)
                + " LIMIT " + pagina.limitConSondeo() + " OFFSET " + pagina.offset();

        var consulta = jdbc.sql(sql);
        for (var e : params.entrySet()) {
            consulta = consulta.param(e.getKey(), e.getValue());
        }
        return consulta.query(PendingTask.class).list();
    }

    /**
     * Los programados para hoy y los vencidos que siguen activos.
     *
     * <p>Si solo mostrara los de hoy, lo que se quedo atras desapareceria de la
     * vista justo cuando mas importa mirarlo.
     */
    public List<PendingTask> deHoy(LocalDate hoy, Paging pagina) {
        return jdbc.sql(SELECCION + """
                 WHERE t.active = true
                   AND t.completed_at IS NULL
                   AND t.scheduled_for IS NOT NULL
                   AND t.scheduled_for <= :hoy
                 ORDER BY t.scheduled_for ASC, t.id ASC
                 LIMIT :limite OFFSET :salto
                """)
                .param("hoy", hoy)
                .param("limite", pagina.limitConSondeo()).param("salto", pagina.offset())
                .query(PendingTask.class).list();
    }

    /**
     * Los pendientes cumplidos dentro de un dia concreto, para «¿que hice hoy?».
     *
     * <p><b>Dos marcas de tiempo, nunca {@code CAST(completed_at AS date)}.</b>
     * {@code completed_at} es {@code timestamptz}: castearlo lo resuelve en la zona
     * del servidor, asi que un pendiente cumplido a las 19:30 en Lima caeria en el dia
     * siguiente si el servidor esta en UTC. Y una expresion sobre la columna inutiliza
     * el indice {@code pending_task_cumplidos}; comparar la columna desnuda lo usa.
     *
     * <p>Las dos fronteras las calcula quien llama, con {@code ClockConfig.ZONA}.
     *
     * @param inicio            comienzo del dia, incluido
     * @param inicioDelSiguiente comienzo del dia siguiente, excluido
     */
    public List<PendingTask> cumplidosEnElDia(UUID responsable, Instant inicio,
                                              Instant inicioDelSiguiente) {
        return jdbc.sql(SELECCION + """
                 WHERE t.owner_id = :responsable
                   AND t.completed_at >= :inicio
                   AND t.completed_at <  :fin
                 ORDER BY t.completed_at ASC, t.id ASC
                """)
                .param("responsable", responsable)
                .param("inicio", Timestamp.from(inicio))
                .param("fin", Timestamp.from(inicioDelSiguiente))
                .query(PendingTask.class).list();
    }

    public List<PendingTask> cumplidos(Paging pagina) {
        return jdbc.sql(SELECCION + """
                 WHERE t.completed_at IS NOT NULL
                 ORDER BY t.completed_at DESC, t.id ASC
                 LIMIT :limite OFFSET :salto
                """)
                .param("limite", pagina.limitConSondeo()).param("salto", pagina.offset())
                .query(PendingTask.class).list();
    }

    private String orden(PendingTaskFilters filtros) {
        String sentido = "asc".equals(filtros.direction()) ? "ASC" : "DESC";
        return switch (filtros.sort()) {
            case "deadline" -> " ORDER BY t.deadline " + sentido + " NULLS LAST, t.id ASC";
            case "priority" -> " ORDER BY lower(btrim(prio.name)) " + sentido + " NULLS LAST, t.id ASC";
            case "title"    -> " ORDER BY lower(btrim(t.title)) " + sentido + ", t.id ASC";
            default         -> " ORDER BY t.scheduled_for " + sentido + " NULLS LAST, t.id ASC";
        };
    }

    public UUID insertar(PendingTaskForm form, UUID responsable, Instant ahora) {
        UUID id = UUID.randomUUID();
        Timestamp momento = Timestamp.from(ahora);
        jdbc.sql("""
                INSERT INTO pending_task
                    (id, owner_id, title, description, pending_task_type_id, priority_id,
                     pending_task_status_id, judicial_case_id, administrative_procedure_id,
                     received_at, registered_at, scheduled_for, deadline,
                     output_document_type, output_document_number, notes, active,
                     created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :desc, :tipo, :prioridad, :estado,
                        :judicial, :administrativo, :recepcion, :registro, :programada,
                        :limite, :docTipo, :docNumero, :notas, true, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", responsable)
                .param("titulo", form.title().strip())
                .param("desc", vacioANulo(form.description()))
                .param("tipo", form.pendingTaskTypeId())
                .param("prioridad", form.priorityId())
                .param("estado", form.pendingTaskStatusId())
                .param("judicial", form.judicialCaseId())
                .param("administrativo", form.administrativeProcedureId())
                .param("recepcion", PendingTaskValidator.fechaNormalizada(form.receivedAt()))
                // La fecha de registro es el dia del alta: no la escribe la persona.
                .param("registro", LocalDate.ofInstant(ahora, ClockConfig.ZONA))
                .param("programada", PendingTaskValidator.fechaNormalizada(form.scheduledFor()))
                .param("limite", PendingTaskValidator.fechaNormalizada(form.deadline()))
                .param("docTipo", vacioANulo(form.outputDocumentType()))
                .param("docNumero", vacioANulo(form.outputDocumentNumber()))
                .param("notas", vacioANulo(form.notes()))
                .param("ahora", momento)
                .update();
        return id;
    }

    public Optional<Map<String, Object>> bloquearParaActuar(UUID id) {
        return jdbc.sql("""
                SELECT id, owner_id, version, completed_at, scheduled_for
                FROM pending_task WHERE id = :id FOR UPDATE
                """).param("id", id).query().listOfRows().stream().findFirst();
    }

    public boolean actualizar(UUID id, PendingTaskForm form, long version, Instant ahora) {
        return jdbc.sql("""
                UPDATE pending_task SET
                    title = :titulo, description = :desc,
                    pending_task_type_id = :tipo, priority_id = :prioridad,
                    pending_task_status_id = :estado,
                    judicial_case_id = :judicial, administrative_procedure_id = :administrativo,
                    received_at = :recepcion, scheduled_for = :programada, deadline = :limite,
                    output_document_type = :docTipo, output_document_number = :docNumero,
                    notes = :notas, updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id).param("version", version)
                .param("titulo", form.title().strip())
                .param("desc", vacioANulo(form.description()))
                .param("tipo", form.pendingTaskTypeId()).param("prioridad", form.priorityId())
                .param("estado", form.pendingTaskStatusId())
                .param("judicial", form.judicialCaseId())
                .param("administrativo", form.administrativeProcedureId())
                .param("recepcion", PendingTaskValidator.fechaNormalizada(form.receivedAt()))
                .param("programada", PendingTaskValidator.fechaNormalizada(form.scheduledFor()))
                .param("limite", PendingTaskValidator.fechaNormalizada(form.deadline()))
                .param("docTipo", vacioANulo(form.outputDocumentType()))
                .param("docNumero", vacioANulo(form.outputDocumentNumber()))
                .param("notas", vacioANulo(form.notes()))
                .param("ahora", Timestamp.from(ahora))
                .update() == 1;
    }

    /**
     * Cuantas veces se reprogramo cada pendiente de la lista.
     *
     * <p><b>Una sola consulta para toda la pagina</b>, no una por fila. El numero se
     * cuenta desde el historial y no se guarda: un contador persistido se
     * desincronizaria en cuanto alguien revirtiera un cumplido.
     */
    public Map<UUID, Integer> reprogramacionesDe(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> conteo = new HashMap<>();
        jdbc.sql("""
                SELECT entity_id, count(*) AS veces
                FROM audit_event
                WHERE entity_type = 'PENDING_TASK'
                  AND action IN ('NOT_COMPLETED', 'RESCHEDULE')
                  AND entity_id = ANY (:ids)
                GROUP BY entity_id
                """)
                .param("ids", ids.toArray(UUID[]::new))
                .query().listOfRows()
                .forEach(fila -> conteo.put((UUID) fila.get("entity_id"),
                        ((Number) fila.get("veces")).intValue()));
        return conteo;
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static void anadirIgual(List<String> condiciones, Map<String, Object> params,
                                    String columna, String nombre, UUID valor) {
        if (valor != null) {
            condiciones.add(columna + " = :" + nombre);
            params.put(nombre, valor);
        }
    }

    /** Los campos que el insumo enumera para pendientes (seccion 34). {@code notes} faltaba. */
    public static final String CONDICION_TEXTO = """
            (t.title ILIKE :q ESCAPE '\\'
             OR t.description ILIKE :q ESCAPE '\\'
             OR t.notes ILIKE :q ESCAPE '\\')""";

    private static String escapar(String valor) {
        return BusquedaDeTexto.escapar(valor);
    }
}
