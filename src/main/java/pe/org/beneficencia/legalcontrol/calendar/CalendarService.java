package pe.org.beneficencia.legalcontrol.calendar;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Alta, correccion y retirada de dias no laborables.
 *
 * <p><b>Cualquier cambio invalida la revision del ano afectado.</b> Si alguien
 * anade un feriado despues de que la jefa confirmara la cobertura, esa
 * confirmacion ya no vale: el calendario cambio y nadie ha vuelto a mirarlo. El
 * sistema prefiere volver a avisar que seguir contando con datos sin revisar.
 *
 * <p>Cambiar un dia de un ano a otro toca <b>ambos</b> anos, en orden ascendente
 * para que dos operaciones simultaneas no se bloqueen mutuamente.
 */
@Service
public class CalendarService {

    private final NonWorkingDayRepository dias;
    private final JdbcClient jdbc;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public CalendarService(NonWorkingDayRepository dias, JdbcClient jdbc,
                           AuditRecorder auditoria, Clock clock) {
        this.dias = dias;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public Optional<String> agregar(LocalDate dia, String descripcion, Tipo tipo, CuentaActual jefa) {
        exigirJefa(jefa);
        if (dia == null) {
            return Optional.of("La fecha es obligatoria.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            return Optional.of("La descripcion es obligatoria.");
        }
        if (dias.diaYaRegistrado(dia, null)) {
            return Optional.of("Ese dia ya esta registrado como no laborable.");
        }

        UUID id = dias.insertar(dia, descripcion, tipo, jefa.id(), clock.instant());
        dias.tocarAno(dia.getYear(), jefa.id(), clock.instant());

        auditoria.registrar("NON_WORKING_DAY", id, "CREATE", jefa.id(), jefa.id(), null,
                Map.of("day", dia.toString(), "description", descripcion.strip(),
                       "kind", tipo.name()), null);
        return Optional.empty();
    }

    @Transactional
    public Optional<String> corregir(UUID id, LocalDate dia, String descripcion, Tipo tipo,
                                     long version, CuentaActual jefa) {
        exigirJefa(jefa);
        var actual = dias.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("dia inexistente"));

        LocalDate anterior = ((java.sql.Date) actual.get("day")).toLocalDate();
        if (dias.diaYaRegistrado(dia, id)) {
            return Optional.of("Ese dia ya esta registrado como no laborable.");
        }

        if (!dias.actualizar(id, dia, descripcion, tipo, version, clock.instant())) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este dia");
        }

        // Si cambio de ano, ambos quedan sin revisar. Orden ascendente por deadlocks.
        int menor = Math.min(anterior.getYear(), dia.getYear());
        int mayor = Math.max(anterior.getYear(), dia.getYear());
        dias.tocarAno(menor, jefa.id(), clock.instant());
        if (mayor != menor) {
            dias.tocarAno(mayor, jefa.id(), clock.instant());
        }

        auditoria.registrar("NON_WORKING_DAY", id, "UPDATE", jefa.id(), jefa.id(),
                Map.of("day", anterior.toString(), "description", actual.get("description"),
                       "kind", actual.get("kind")),
                Map.of("day", dia.toString(), "description", descripcion.strip(),
                       "kind", tipo.name()), null);
        return Optional.empty();
    }

    @Transactional
    public void retirar(UUID id, long version, CuentaActual jefa) {
        exigirJefa(jefa);
        var actual = dias.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("dia inexistente"));
        LocalDate dia = ((java.sql.Date) actual.get("day")).toLocalDate();

        // La evidencia se escribe ANTES de borrar: retirar un dia no borra su historia.
        auditoria.registrar("NON_WORKING_DAY", id, "DELETE", jefa.id(), jefa.id(),
                Map.of("day", dia.toString(), "description", actual.get("description"),
                       "kind", actual.get("kind")),
                null, null);

        if (!dias.eliminar(id, version)) {
            throw new ErrorHandling.ConflictoDeEdicion("otra persona modifico este dia");
        }
        dias.tocarAno(dia.getYear(), jefa.id(), clock.instant());
    }

    /** ¿Esta revisada la cobertura del ano para su revision tecnica actual? */
    public boolean cubierto(int ano) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM calendar_year y
                JOIN calendar_review r ON r.year = y.year AND r.reviewed_revision = y.revision
                WHERE y.year = :ano
                """).param("ano", ano).query(Integer.class).single();
        return total != null && total > 0 && dias.contarDelAno(ano) > 0;
    }

    private void exigirJefa(CuentaActual actor) {
        if (actor == null || !actor.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra el calendario");
        }
    }
}
