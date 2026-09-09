package pe.org.beneficencia.legalcontrol.judicialcase;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import pe.org.beneficencia.legalcontrol.shared.BusquedaDeTexto;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Consultas de expedientes con SQL explicito.
 *
 * <p>El listado se resuelve en <b>una sola consulta</b> con joins al responsable
 * y al catalogo: nada de una consulta por fila. Con 5.000 expedientes esa
 * diferencia es la que decide si la pantalla abre en un segundo o en veinte.
 *
 * <p>Los filtros de texto van parametrizados y con {@code %} y {@code _}
 * escapados, para que un numero de expediente con guion bajo no se convierta en
 * comodin.
 */
@Repository
public class JudicialCaseRepository {

    private static final String SELECCION = """
            SELECT c.id, c.sequence_number, c.owner_id, u.name AS owner_name,
                   c.case_number, c.claimant, c.respondent, c.subject,
                   c.procedural_status_id, s.name AS procedural_status_name,
                   c.last_procedural_action, c.next_procedural_action,
                   c.last_action_date, c.deadline, c.amount, c.property_address,
                   c.notes, c.management_actions, c.active,
                   c.created_at, c.updated_at, c.version
            FROM judicial_case c
            JOIN app_user u ON u.id = c.owner_id
            LEFT JOIN procedural_status s ON s.id = c.procedural_status_id
            """;

    private final JdbcClient jdbc;

    public JudicialCaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<JudicialCase> porId(UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id")
                .param("id", id)
                .query(JudicialCase.class)
                .optional();
    }

    /**
     * Devuelve hasta {@code size + 1} filas: la de mas solo sirve para saber si
     * hay pagina siguiente, sin pagar un COUNT sobre toda la tabla.
     */
    public List<JudicialCase> listar(CaseFilters filtros, Paging pagina, java.time.LocalDate hoy) {
        List<String> condiciones = new ArrayList<>();
        Map<String, Object> params = new java.util.HashMap<>();

        switch (filtros.visibility()) {
            case "active"   -> condiciones.add("c.active = true");
            case "inactive" -> condiciones.add("c.active = false");
            default         -> { }   // "all": sin condicion
        }
        if (filtros.q() != null && !filtros.q().isBlank()) {
            condiciones.add(CONDICION_TEXTO);
            params.put("q", BusquedaDeTexto.comodin(filtros.q()));
        }
        if (filtros.ownerId() != null) {
            condiciones.add("c.owner_id = :ownerId");
            params.put("ownerId", filtros.ownerId());
        }
        if (filtros.proceduralStatusId() != null) {
            condiciones.add("c.procedural_status_id = :statusId");
            params.put("statusId", filtros.proceduralStatusId());
        }
        if (filtros.subject() != null && !filtros.subject().isBlank()) {
            condiciones.add("c.subject ILIKE :subject ESCAPE '\\'");
            params.put("subject", "%" + escapar(filtros.subject().strip()) + "%");
        }
        switch (filtros.deadlinePresence()) {
            case "with"    -> condiciones.add("c.deadline IS NOT NULL");
            case "without" -> condiciones.add("c.deadline IS NULL");
            default        -> { }
        }
        if (Boolean.TRUE.equals(filtros.overdue())) {
            condiciones.add("c.deadline IS NOT NULL AND c.deadline < :hoy");
            params.put("hoy", hoy);
        } else if (Boolean.FALSE.equals(filtros.overdue())) {
            condiciones.add("(c.deadline IS NULL OR c.deadline >= :hoy)");
            params.put("hoy", hoy);
        }

        String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
        String sql = SELECCION + where + orden(filtros)
                + " LIMIT " + pagina.limitConSondeo() + " OFFSET " + pagina.offset();

        var consulta = jdbc.sql(sql);
        for (var e : params.entrySet()) {
            consulta = consulta.param(e.getKey(), e.getValue());
        }
        return consulta.query(JudicialCase.class).list();
    }

    /** Orden contra lista cerrada; NULL de fecha siempre al final; desempate por id. */
    private String orden(CaseFilters filtros) {
        boolean asc = "asc".equals(filtros.direction());
        String sentido = asc ? "ASC" : "DESC";
        return switch (filtros.sort()) {
            case "owner"    -> " ORDER BY lower(btrim(u.name)) " + sentido + ", c.id ASC";
            case "deadline" -> " ORDER BY c.deadline " + sentido + " NULLS LAST, c.id ASC";
            default         -> " ORDER BY lower(btrim(c.case_number)) " + sentido + ", c.id ASC";
        };
    }

    public UUID insertar(JudicialCaseForm form, UUID responsable, java.time.Instant ahora) {
        UUID id = UUID.randomUUID();
        Timestamp momento = Timestamp.from(ahora);
        jdbc.sql("""
                INSERT INTO judicial_case
                    (id, sequence_number, owner_id, case_number, claimant, respondent, subject,
                     procedural_status_id, last_procedural_action, next_procedural_action,
                     last_action_date, deadline, amount, property_address, notes,
                     management_actions, active, created_at, updated_at, version)
                VALUES
                    (:id, :seq, :owner, :numero, :demandante, :demandado, :materia,
                     :estado, :ultimoActo, :siguienteActo,
                     :fechaUltimoActo, :fechaLimite, :monto, :direccion, :notas,
                     :gerencia, true, :ahora, :ahora, 1)
                """)
                .param("id", id)
                .param("seq", JudicialCaseValidator.enteroNormalizado(form.sequenceNumber()))
                .param("owner", responsable)
                .param("numero", form.caseNumber().strip())
                .param("demandante", vacioANulo(form.claimant()))
                .param("demandado", vacioANulo(form.respondent()))
                .param("materia", vacioANulo(form.subject()))
                .param("estado", form.proceduralStatusId())
                .param("ultimoActo", vacioANulo(form.lastProceduralAction()))
                .param("siguienteActo", vacioANulo(form.nextProceduralAction()))
                .param("fechaUltimoActo", JudicialCaseValidator.fechaNormalizada(form.lastActionDate()))
                .param("fechaLimite", JudicialCaseValidator.fechaNormalizada(form.deadline()))
                .param("monto", JudicialCaseValidator.montoNormalizado(form.amount()))
                .param("direccion", vacioANulo(form.propertyAddress()))
                .param("notas", vacioANulo(form.notes()))
                .param("gerencia", vacioANulo(form.managementActions()))
                .param("ahora", momento)
                .update();
        return id;
    }

    public boolean numeroYaUsado(String numero) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM judicial_case
                WHERE lower(btrim(case_number)) = lower(btrim(:numero))
                """).param("numero", numero).query(Integer.class).single();
        return total != null && total > 0;
    }


    /** Bloquea la fila para modificarla y devuelve su version y responsable actuales. */
    public Optional<Map<String, Object>> bloquearParaEditar(UUID id) {
        return jdbc.sql("SELECT id, owner_id, version FROM judicial_case WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query()
                .listOfRows()
                .stream().findFirst();
    }

    /**
     * Actualiza solo si la version coincide con la que traia el formulario.
     *
     * @return true si se actualizo; false si otra persona lo cambio entretanto
     */
    public boolean actualizar(UUID id, JudicialCaseForm form, long versionEsperada,
                              java.time.Instant ahora) {
        int filas = jdbc.sql("""
                UPDATE judicial_case SET
                    sequence_number = :seq,
                    case_number = :numero,
                    claimant = :demandante,
                    respondent = :demandado,
                    subject = :materia,
                    procedural_status_id = :estado,
                    last_procedural_action = :ultimoActo,
                    next_procedural_action = :siguienteActo,
                    last_action_date = :fechaUltimoActo,
                    deadline = :fechaLimite,
                    amount = :monto,
                    property_address = :direccion,
                    notes = :notas,
                    management_actions = :gerencia,
                    updated_at = :ahora,
                    version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id)
                .param("version", versionEsperada)
                .param("seq", JudicialCaseValidator.enteroNormalizado(form.sequenceNumber()))
                .param("numero", form.caseNumber().strip())
                .param("demandante", vacioANulo(form.claimant()))
                .param("demandado", vacioANulo(form.respondent()))
                .param("materia", vacioANulo(form.subject()))
                .param("estado", form.proceduralStatusId())
                .param("ultimoActo", vacioANulo(form.lastProceduralAction()))
                .param("siguienteActo", vacioANulo(form.nextProceduralAction()))
                .param("fechaUltimoActo", JudicialCaseValidator.fechaNormalizada(form.lastActionDate()))
                .param("fechaLimite", JudicialCaseValidator.fechaNormalizada(form.deadline()))
                .param("monto", JudicialCaseValidator.montoNormalizado(form.amount()))
                .param("direccion", vacioANulo(form.propertyAddress()))
                .param("notas", vacioANulo(form.notes()))
                .param("gerencia", vacioANulo(form.managementActions()))
                .param("ahora", Timestamp.from(ahora))
                .update();
        return filas == 1;
    }

    /** Cambia solo la visibilidad. NO toca el estado procesal (FR-023). */
    public boolean cambiarVisibilidad(UUID id, boolean visible, long versionEsperada,
                                      java.time.Instant ahora) {
        int filas = jdbc.sql("""
                UPDATE judicial_case
                SET active = :visible, updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id).param("visible", visible)
                .param("version", versionEsperada).param("ahora", Timestamp.from(ahora))
                .update();
        return filas == 1;
    }

    public boolean numeroYaUsadoPorOtro(String numero, UUID excepto) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM judicial_case
                WHERE lower(btrim(case_number)) = lower(btrim(:numero)) AND id <> :excepto
                """).param("numero", numero).param("excepto", excepto)
                .query(Integer.class).single();
        return total != null && total > 0;
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /**
     * Los campos que el insumo enumera para judiciales (seccion 34).
     *
     * <p>Es publica y la comparte el buscador global: si cada uno tuviera su lista,
     * la misma palabra daria resultados distintos segun donde se escriba (RF-012).
     * {@code subject} y {@code notes} faltaban hasta la 006 —el filtro solo miraba
     * numero, demandante y demandado—, y por eso un caso que solo coincidia por su
     * materia no se encontraba.
     */
    public static final String CONDICION_TEXTO = """
            (c.case_number ILIKE :q ESCAPE '\\'
             OR c.claimant ILIKE :q ESCAPE '\\'
             OR c.respondent ILIKE :q ESCAPE '\\'
             OR c.subject ILIKE :q ESCAPE '\\'
             OR c.notes ILIKE :q ESCAPE '\\')""";

    private static String escapar(String valor) {
        return BusquedaDeTexto.escapar(valor);
    }
}
