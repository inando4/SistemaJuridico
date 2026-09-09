package pe.org.beneficencia.legalcontrol.administrativeprocedure;

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

/**
 * Consultas de procedimientos administrativos con SQL explicito.
 *
 * <p>El listado se resuelve en <b>una sola consulta</b> con joins al responsable y
 * al catalogo. Con la base al otro lado del pais, una consulta por fila serian
 * veinticinco viajes de ida y vuelta para pintar una pantalla.
 *
 * <p>Los filtros de texto van parametrizados y con {@code %} y {@code _} escapados,
 * para que un numero con guion bajo no se convierta en comodin.
 */
@Repository
public class AdministrativeProcedureRepository {

    private static final String SELECCION = """
            SELECT p.id, p.sequence_number, p.owner_id, u.name AS owner_name,
                   p.file_number, p.requesting_area, p.request,
                   p.administrative_status_id, s.name AS administrative_status_name,
                   p.received_at, p.deadline, p.notes, p.active,
                   p.created_at, p.updated_at, p.version
            FROM administrative_procedure p
            JOIN app_user u ON u.id = p.owner_id
            LEFT JOIN administrative_status s ON s.id = p.administrative_status_id
            """;

    private final JdbcClient jdbc;

    public AdministrativeProcedureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AdministrativeProcedure> porId(UUID id) {
        return jdbc.sql(SELECCION + " WHERE p.id = :id")
                .param("id", id)
                .query(AdministrativeProcedure.class)
                .optional();
    }

    /** Devuelve hasta {@code size + 1} filas: la de mas dice si hay pagina siguiente. */
    public List<AdministrativeProcedure> listar(ProcedureFilters filtros, Paging pagina,
                                                LocalDate hoy) {
        List<String> condiciones = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        switch (filtros.visibility()) {
            case "active"   -> condiciones.add("p.active = true");
            case "inactive" -> condiciones.add("p.active = false");
            default         -> { }
        }
        if (filtros.q() != null && !filtros.q().isBlank()) {
            condiciones.add(CONDICION_TEXTO);
            params.put("q", BusquedaDeTexto.comodin(filtros.q()));
        }
        if (filtros.ownerId() != null) {
            condiciones.add("p.owner_id = :ownerId");
            params.put("ownerId", filtros.ownerId());
        }
        if (filtros.administrativeStatusId() != null) {
            condiciones.add("p.administrative_status_id = :statusId");
            params.put("statusId", filtros.administrativeStatusId());
        }
        if (filtros.requestingArea() != null && !filtros.requestingArea().isBlank()) {
            condiciones.add("p.requesting_area ILIKE :area ESCAPE '\\'");
            params.put("area", "%" + escapar(filtros.requestingArea().strip()) + "%");
        }
        switch (filtros.deadlinePresence()) {
            case "with"    -> condiciones.add("p.deadline IS NOT NULL");
            case "without" -> condiciones.add("p.deadline IS NULL");
            default        -> { }
        }
        if (Boolean.TRUE.equals(filtros.overdue())) {
            condiciones.add("p.deadline IS NOT NULL AND p.deadline < :hoy");
            params.put("hoy", hoy);
        } else if (Boolean.FALSE.equals(filtros.overdue())) {
            condiciones.add("(p.deadline IS NULL OR p.deadline >= :hoy)");
            params.put("hoy", hoy);
        }

        String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
        String sql = SELECCION + where + orden(filtros)
                + " LIMIT " + pagina.limitConSondeo() + " OFFSET " + pagina.offset();

        var consulta = jdbc.sql(sql);
        for (var e : params.entrySet()) {
            consulta = consulta.param(e.getKey(), e.getValue());
        }
        return consulta.query(AdministrativeProcedure.class).list();
    }

    /** Orden contra lista cerrada; NULL de fecha al final; desempate por id. */
    private String orden(ProcedureFilters filtros) {
        String sentido = "asc".equals(filtros.direction()) ? "ASC" : "DESC";
        return switch (filtros.sort()) {
            case "owner"    -> " ORDER BY lower(btrim(u.name)) " + sentido + ", p.id ASC";
            case "deadline" -> " ORDER BY p.deadline " + sentido + " NULLS LAST, p.id ASC";
            default         -> " ORDER BY lower(btrim(p.file_number)) " + sentido + ", p.id ASC";
        };
    }

    public UUID insertar(AdministrativeProcedureForm form, UUID responsable, Instant ahora) {
        UUID id = UUID.randomUUID();
        Timestamp momento = Timestamp.from(ahora);
        jdbc.sql("""
                INSERT INTO administrative_procedure
                    (id, sequence_number, owner_id, file_number, requesting_area, request,
                     administrative_status_id, received_at, deadline, notes, active,
                     created_at, updated_at, version)
                VALUES (:id, :seq, :owner, :numero, :area, :pedido, :estado,
                        :recepcion, :limite, :notas, true, :ahora, :ahora, 1)
                """)
                .param("id", id)
                .param("seq", AdministrativeProcedureValidator.enteroNormalizado(form.sequenceNumber()))
                .param("owner", responsable)
                .param("numero", form.fileNumber().strip())
                .param("area", vacioANulo(form.requestingArea()))
                .param("pedido", vacioANulo(form.request()))
                .param("estado", form.administrativeStatusId())
                .param("recepcion", AdministrativeProcedureValidator.fechaNormalizada(form.receivedAt()))
                .param("limite", AdministrativeProcedureValidator.fechaNormalizada(form.deadline()))
                .param("notas", vacioANulo(form.notes()))
                .param("ahora", momento)
                .update();
        return id;
    }

    /** Bloquea la fila para modificarla y devuelve su version y responsable actuales. */
    public Optional<Map<String, Object>> bloquearParaEditar(UUID id) {
        return jdbc.sql("""
                SELECT id, owner_id, version FROM administrative_procedure
                WHERE id = :id FOR UPDATE
                """)
                .param("id", id).query().listOfRows().stream().findFirst();
    }

    /** @return true si se actualizo; false si otra persona lo cambio entretanto */
    public boolean actualizar(UUID id, AdministrativeProcedureForm form, long versionEsperada,
                              Instant ahora) {
        return jdbc.sql("""
                UPDATE administrative_procedure SET
                    sequence_number = :seq,
                    file_number = :numero,
                    requesting_area = :area,
                    request = :pedido,
                    administrative_status_id = :estado,
                    received_at = :recepcion,
                    deadline = :limite,
                    notes = :notas,
                    updated_at = :ahora,
                    version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id).param("version", versionEsperada)
                .param("seq", AdministrativeProcedureValidator.enteroNormalizado(form.sequenceNumber()))
                .param("numero", form.fileNumber().strip())
                .param("area", vacioANulo(form.requestingArea()))
                .param("pedido", vacioANulo(form.request()))
                .param("estado", form.administrativeStatusId())
                .param("recepcion", AdministrativeProcedureValidator.fechaNormalizada(form.receivedAt()))
                .param("limite", AdministrativeProcedureValidator.fechaNormalizada(form.deadline()))
                .param("notas", vacioANulo(form.notes()))
                .param("ahora", Timestamp.from(ahora))
                .update() == 1;
    }

    /** Cambia solo la visibilidad. NO toca el estado. */
    public boolean cambiarVisibilidad(UUID id, boolean visible, long versionEsperada, Instant ahora) {
        return jdbc.sql("""
                UPDATE administrative_procedure
                SET active = :visible, updated_at = :ahora, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", id).param("visible", visible)
                .param("version", versionEsperada).param("ahora", Timestamp.from(ahora))
                .update() == 1;
    }

    /**
     * ¿Ya existe este numero entre los procedimientos administrativos?
     *
     * <p>NO se comprueba contra expedientes judiciales: sus series son
     * independientes y un mismo numero puede existir en ambos registros.
     */
    public boolean numeroYaUsado(String numero) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM administrative_procedure
                WHERE lower(btrim(file_number)) = lower(btrim(:numero))
                """).param("numero", numero).query(Integer.class).single();
        return total != null && total > 0;
    }

    public boolean numeroYaUsadoPorOtro(String numero, UUID excepto) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM administrative_procedure
                WHERE lower(btrim(file_number)) = lower(btrim(:numero)) AND id <> :excepto
                """).param("numero", numero).param("excepto", excepto)
                .query(Integer.class).single();
        return total != null && total > 0;
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /** Los campos que el insumo enumera para administrativos (seccion 34). {@code notes} faltaba. */
    public static final String CONDICION_TEXTO = """
            (p.file_number ILIKE :q ESCAPE '\\'
             OR p.requesting_area ILIKE :q ESCAPE '\\'
             OR p.request ILIKE :q ESCAPE '\\'
             OR p.notes ILIKE :q ESCAPE '\\')""";

    private static String escapar(String valor) {
        return BusquedaDeTexto.escapar(valor);
    }
}
