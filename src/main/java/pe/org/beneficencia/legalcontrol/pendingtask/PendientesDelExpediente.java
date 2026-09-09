package pe.org.beneficencia.legalcontrol.pendingtask;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * El bloque «Pendientes relacionados» de las fichas de expediente.
 *
 * <p>Existe como componente y no escrito dos veces porque las secciones 28 y 30 del
 * insumo piden lo mismo en dos pantallas distintas, y de dos copias siempre hay una
 * que se queda atras.
 *
 * <p>No tiene consulta propia: pide la lista a
 * {@link PendingTaskRepository#listar} con los filtros de
 * {@link PendingTaskFilters#deExpedienteJudicial}. Asi el enlace «Ver todos» apunta
 * literalmente al mismo conjunto en el mismo orden, y no hay dos condiciones que
 * puedan separarse. Cuando eso paso en la 004, una tarjeta del panel contaba
 * pendientes que su propio listado no mostraba.
 *
 * <p><b>Una consulta.</b> {@code SELECCION} ya trae el nombre del responsable en su
 * {@code JOIN app_user}, asi que no hay resolucion fila por fila. La instantanea del
 * calendario llega de fuera, ya cargada por la ficha para su propio plazo: pedir
 * otra seria una consulta mas por nada.
 */
@Component
public class PendientesDelExpediente {

    private final PendingTaskRepository pendientes;
    private final DeadlineEvaluator plazos;

    public PendientesDelExpediente(PendingTaskRepository pendientes, DeadlineEvaluator plazos) {
        this.pendientes = pendientes;
        this.plazos = plazos;
    }

    /** Los de un expediente judicial. */
    public void poblarJudicial(Model modelo, UUID expediente, LocalDate hoy,
                               CalendarSnapshot calendario) {
        poblar(modelo, PendingTaskFilters.deExpedienteJudicial(expediente), hoy, calendario);
    }

    /** Los de un procedimiento administrativo. */
    public void poblarAdministrativo(Model modelo, UUID procedimiento, LocalDate hoy,
                                     CalendarSnapshot calendario) {
        poblar(modelo, PendingTaskFilters.deExpedienteAdministrativo(procedimiento), hoy,
                calendario);
    }

    private void poblar(Model modelo, PendingTaskFilters filtros, LocalDate hoy,
                        CalendarSnapshot calendario) {
        Paging pagina = Paging.of(0);
        List<PendingTask> filas = new ArrayList<>(pendientes.listar(filtros, pagina, hoy));

        // El sondeo de una fila de mas dice si hay siguiente pagina sin contar el
        // total. Un count(*) seria una segunda consulta para un dato derivado que
        // el principio V prohibe persistir y que esta fila ya responde.
        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas.remove(filas.size() - 1);
        }

        Map<UUID, DeadlineView> vistas = new LinkedHashMap<>();
        for (PendingTask t : filas) {
            vistas.put(t.id(), plazos.evaluar(t.deadline(), hoy, calendario));
        }

        modelo.addAttribute("pendientesRelacionados", filas);
        modelo.addAttribute("hayMasPendientes", hayMas);
        modelo.addAttribute("plazosDePendientes", vistas);
        modelo.addAttribute("enlaceAPendientes", "/pendientes" + filtros.comoQuery(0));
        modelo.addAttribute("enlaceANuevoPendiente", enlaceDeAlta(filtros));
    }

    private String enlaceDeAlta(PendingTaskFilters filtros) {
        if (filtros.judicialCaseId() != null) {
            return "/pendientes/nuevo?judicialCaseId=" + filtros.judicialCaseId();
        }
        return "/pendientes/nuevo?administrativeProcedureId="
                + filtros.administrativeProcedureId();
    }
}
