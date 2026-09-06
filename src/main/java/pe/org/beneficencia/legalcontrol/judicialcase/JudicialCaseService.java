package pe.org.beneficencia.legalcontrol.judicialcase;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;

/**
 * Alta de expedientes.
 *
 * <p><b>El responsable lo fija el servidor</b> con el usuario que crea el
 * registro. La asignacion entre abogados es funcionalidad posterior, asi que un
 * intento de asignar a otra persona se rechaza en vez de ignorarse: si alguien
 * lo pidio, tiene que enterarse de que no ocurrio.
 *
 * <p>El alta y su evidencia van en la misma transaccion. Si la auditoria falla,
 * el expediente no se crea.
 */
@Service
public class JudicialCaseService {

    private final JudicialCaseRepository expedientes;
    private final JudicialCaseValidator validador;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public JudicialCaseService(JudicialCaseRepository expedientes, JudicialCaseValidator validador,
                               AuditRecorder auditoria, Clock clock) {
        this.expedientes = expedientes;
        this.validador = validador;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public record Resultado(UUID id, Map<String, String> errores) {
        public boolean correcto() {
            return errores.isEmpty();
        }
        static Resultado con(Map<String, String> errores) {
            return new Resultado(null, errores);
        }
    }

    @Transactional
    public Resultado crear(JudicialCaseForm form, UUID responsable) {
        Map<String, String> errores = validador.validar(form);
        if (!errores.isEmpty()) {
            return Resultado.con(errores);
        }

        String numero = form.caseNumber().strip();
        if (expedientes.numeroYaUsado(numero)) {
            errores.put("caseNumber",
                    "Ya existe un expediente con ese numero, aunque sea de otra persona "
                    + "o este oculto del listado.");
            return Resultado.con(errores);
        }

        UUID id;
        try {
            id = expedientes.insertar(form, responsable, clock.instant());
        } catch (DuplicateKeyException carrera) {
            // Dos altas simultaneas con el mismo numero: la restriccion unica de la
            // base decide, y aqui se traduce a un error del formulario.
            errores.put("caseNumber", "Ya existe un expediente con ese numero.");
            return Resultado.con(errores);
        }

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("caseNumber", numero);
        despues.put("ownerId", responsable.toString());
        if (form.subject() != null && !form.subject().isBlank()) {
            despues.put("subject", form.subject().strip());
        }
        if (form.deadline() != null && !form.deadline().isBlank()) {
            despues.put("deadline", form.deadline().strip());
        }

        // Alta: el «antes» es ausencia, no un mapa vacio.
        auditoria.registrar("JUDICIAL_CASE", id, "CREATE", responsable, responsable,
                null, despues, null);

        return new Resultado(id, Map.of());
    }
}
