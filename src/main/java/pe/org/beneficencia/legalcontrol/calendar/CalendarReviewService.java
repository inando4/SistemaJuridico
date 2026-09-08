package pe.org.beneficencia.legalcontrol.calendar;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Confirmacion de que una persona reviso el calendario de un ano.
 *
 * <p><b>Tener entradas no acredita cobertura.</b> Alguien podria cargar el 1 de
 * enero y marcharse; el sistema daria por bueno todo el ano. Por eso hace falta
 * una declaracion explicita, y con menos de cinco dias se pide un reconocimiento
 * adicional: en Peru un ano tiene bastantes mas feriados que cuatro.
 *
 * <p><b>El servidor cuenta.</b> Nunca acepta el total que venga del formulario:
 * seria confiar en el cliente para decidir si el calendario esta completo.
 */
@Service
public class CalendarReviewService {

    public static final int UMBRAL_CANTIDAD_BAJA = 5;

    private final NonWorkingDayRepository dias;
    private final JdbcClient jdbc;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public CalendarReviewService(NonWorkingDayRepository dias, JdbcClient jdbc,
                                 AuditRecorder auditoria, Clock clock) {
        this.dias = dias;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * @param revisionObservada la que el formulario vio al abrirse
     * @return vacio si se confirmo; el motivo del rechazo si no
     */
    @Transactional
    public Optional<String> confirmar(int ano, long revisionObservada, boolean anoCompletoRevisado,
                                      boolean cantidadBajaReconocida, CuentaActual jefa) {
        if (jefa == null || !jefa.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura confirma la cobertura");
        }
        if (!anoCompletoRevisado) {
            return Optional.of("Debe declarar que revisó el calendario completo de este año.");
        }

        Optional<Long> revisionActual = dias.revisionActual(ano);
        if (revisionActual.isEmpty()) {
            return Optional.of("Este año no tiene ningún día registrado. "
                    + "Cargue el calendario antes de confirmarlo.");
        }
        if (revisionActual.get() != revisionObservada) {
            throw new ErrorHandling.ConflictoDeEdicion(
                    "El calendario cambio mientras revisaba. Vuelva a revisarlo.");
        }

        // El conteo se hace aqui dentro, con el bloqueo tomado.
        int cantidad = dias.contarDelAno(ano);
        if (cantidad == 0) {
            return Optional.of("No se puede confirmar un año sin ningún día registrado.");
        }
        if (cantidad < UMBRAL_CANTIDAD_BAJA && !cantidadBajaReconocida) {
            return Optional.of("Numero inusualmente bajo de dias no laborables ("
                    + cantidad + "). Confirme que asi es como debe quedar.");
        }

        jdbc.sql("""
                INSERT INTO calendar_review (id, year, reviewed_revision, reviewed_by,
                                             reviewed_at, full_year_reviewed,
                                             low_count_acknowledged)
                VALUES (:id, :ano, :revision, :jefa, :ahora, true, :reconocida)
                ON CONFLICT (year, reviewed_revision) DO NOTHING
                """)
                .param("id", UUID.randomUUID()).param("ano", ano)
                .param("revision", revisionObservada).param("jefa", jefa.id())
                .param("ahora", Timestamp.from(clock.instant()))
                .param("reconocida", cantidadBajaReconocida)
                .update();

        auditoria.registrar("CALENDAR_REVIEW", UUID.nameUUIDFromBytes(
                        (ano + ":" + revisionObservada).getBytes()),
                "REVIEW", jefa.id(), jefa.id(), null,
                Map.of("year", ano, "revision", revisionObservada, "count", cantidad), null);

        return Optional.empty();
    }
}
