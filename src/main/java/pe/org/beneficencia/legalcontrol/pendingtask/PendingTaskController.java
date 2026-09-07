package pe.org.beneficencia.legalcontrol.pendingtask;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Listado, alta y ficha de pendientes.
 *
 * <p>Rutas en espanol, fijadas por el insumo (secciones 25, 26 y 32).
 */
@Controller
public class PendingTaskController {

    private final PendingTaskRepository pendientes;
    private final PendingTaskService servicio;
    private final PendingTaskAuthorization permisos;
    private final PendingTaskCatalogs catalogos;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final PendingTaskActionService acciones;
    private final AuditQueryRepository historial;
    private final Clock clock;

    public PendingTaskController(PendingTaskRepository pendientes, PendingTaskService servicio,
                                 PendingTaskAuthorization permisos, PendingTaskCatalogs catalogos,
                                 CalendarRepository calendario, DeadlineEvaluator plazos,
                                 PendingTaskActionService acciones,
                                 AuditQueryRepository historial, Clock clock) {
        this.pendientes = pendientes;
        this.servicio = servicio;
        this.permisos = permisos;
        this.catalogos = catalogos;
        this.calendario = calendario;
        this.plazos = plazos;
        this.acciones = acciones;
        this.historial = historial;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/pendientes")
    public String listado(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) UUID priorityId,
            @RequestParam(required = false) UUID statusId,
            @RequestParam(defaultValue = "any") String linkedTo,
            @RequestParam(defaultValue = "any") String deadlinePresence,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "active") String visibility,
            @RequestParam(defaultValue = "scheduledFor") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            Model modelo) {

        var filtros = new PendingTaskFilters(q, ownerId, typeId, priorityId, statusId, linkedTo,
                deadlinePresence, overdue, visibility, sort, direction, page);

        if (!filtros.valido()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Parametros de filtro no validos");
        }

        LocalDate hoy = LocalDate.now(clock);
        Paging pagina = Paging.of(page);
        List<PendingTask> filas = pendientes.listar(filtros, pagina, hoy);

        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas = filas.subList(0, pagina.size());
        }

        poblarPlazos(modelo, filas, hoy);
        modelo.addAttribute("pendientes", filas);
        modelo.addAttribute("filtros", filtros);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("queryAnterior", filtros.comoQuery(Math.max(0, page - 1)));
        modelo.addAttribute("querySiguiente", filtros.comoQuery(page + 1));
        modelo.addAttribute("tituloPagina", "Pendientes");
        return "pending-tasks/list";
    }

    @GetMapping("/pendientes/nuevo")
    public String formularioNuevo(Model modelo) {
        catalogos.poblar(modelo);
        modelo.addAttribute("form", PendingTaskForm.nuevo());
        modelo.addAttribute("errores", Map.of());
        modelo.addAttribute("tituloPagina", "Nuevo pendiente");
        return "pending-tasks/form";
    }

    @PostMapping("/pendientes")
    public String crear(@ModelAttribute PendingTaskForm form, HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesion");
        }

        var resultado = servicio.crear(form, actual.id());
        if (resultado.correcto()) {
            return "redirect:/pendientes/" + resultado.id();
        }

        catalogos.poblar(modelo);
        modelo.addAttribute("form", form);
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Nuevo pendiente");
        return "pending-tasks/form";
    }

    @GetMapping("/pendientes/{id}")
    public String ficha(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        PendingTask t = pendientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("pendiente inexistente"));

        LocalDate hoy = LocalDate.now(clock);
        poblarPlazos(modelo, List.of(t), hoy);
        modelo.addAttribute("pendiente", t);
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("puedeActuar",
                permisos.puedeActuar(usuarioActual(sesion), t.ownerId()));
        modelo.addAttribute("tituloPagina", t.title());
        return "pending-tasks/detail";
    }

    /**
     * Interpreta el plazo de cada fila con <b>una sola</b> lectura del calendario,
     * y calcula la antiguedad de los que no tienen fecha limite.
     */
    private void poblarPlazos(Model modelo, List<PendingTask> filas, LocalDate hoy) {
        CalendarSnapshot instantanea = calendario.paraListado(hoy);

        Map<UUID, DeadlineView> vistas = new LinkedHashMap<>();
        Map<UUID, Integer> antiguedades = new LinkedHashMap<>();

        for (PendingTask t : filas) {
            vistas.put(t.id(), plazos.evaluar(t.deadline(), hoy, instantanea));

            // Sin fecha limite, la referencia es la de recepcion (seccion 19).
            if (t.deadline() == null && t.receivedAt() != null && !t.cumplido()) {
                plazos.diasHabilesTranscurridos(t.receivedAt(), hoy, instantanea)
                        .ifPresent(dias -> antiguedades.put(t.id(), dias));
            }
        }
        modelo.addAttribute("plazos", vistas);
        modelo.addAttribute("antiguedades", antiguedades);
        modelo.addAttribute("umbralSinPlazo", DeadlineEvaluator.UMBRAL_SIN_PLAZO);
    }

    @GetMapping("/pendientes/{id}/editar")
    public String formularioEdicion(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        PendingTask t = pendientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("pendiente inexistente"));

        if (!permisos.puedeActuar(usuarioActual(sesion), t.ownerId())) {
            throw new ErrorHandling.SinPermiso("no puede actuar sobre pendientes ajenos");
        }

        catalogos.poblar(modelo);
        modelo.addAttribute("form", desdePendiente(t));
        modelo.addAttribute("pendiente", t);
        modelo.addAttribute("errores", Map.of());
        modelo.addAttribute("tituloPagina", "Editar " + t.title());
        return "pending-tasks/edit";
    }

    @PostMapping("/pendientes/{id}")
    public String editar(@PathVariable UUID id, @ModelAttribute PendingTaskForm form,
                         HttpSession sesion, Model modelo) {
        var resultado = servicio.editar(id, form, usuarioActual(sesion));
        if (resultado.correcto()) {
            return "redirect:/pendientes/" + id;
        }
        catalogos.poblar(modelo);
        modelo.addAttribute("form", form);
        modelo.addAttribute("pendiente", pendientes.porId(id).orElseThrow());
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Editar pendiente");
        return "pending-tasks/edit";
    }

    @PostMapping("/pendientes/{id}/cumplir")
    public String cumplir(@PathVariable UUID id, @RequestParam long version,
                          HttpSession sesion, RedirectAttributes flash) {
        acciones.marcarCumplido(id, version, usuarioActual(sesion))
                .ifPresent(motivo -> flash.addFlashAttribute("error", motivo));
        return "redirect:/pendientes/" + id;
    }

    @PostMapping("/pendientes/{id}/revertir")
    public String revertir(@PathVariable UUID id, @RequestParam long version,
                           @RequestParam(required = false) String motivo,
                           HttpSession sesion, RedirectAttributes flash) {
        acciones.revertirCumplimiento(id, version, motivo, usuarioActual(sesion))
                .ifPresent(problema -> flash.addFlashAttribute("error", problema));
        return "redirect:/pendientes/" + id;
    }

    @PostMapping("/pendientes/{id}/no-cumplido")
    public String noCumplido(@PathVariable UUID id, @RequestParam long version,
                             @RequestParam(required = false) String motivo,
                             HttpSession sesion, RedirectAttributes flash) {
        acciones.declararNoCumplido(id, version, motivo, usuarioActual(sesion))
                .ifPresent(problema -> flash.addFlashAttribute("error", problema));
        return "redirect:/pendientes/" + id;
    }

    @PostMapping("/pendientes/{id}/reprogramar")
    public String reprogramar(@PathVariable UUID id, @RequestParam long version,
                              @RequestParam String scheduledFor,
                              @RequestParam(required = false) String motivo,
                              HttpSession sesion, RedirectAttributes flash) {
        LocalDate nueva = PendingTaskValidator.fechaNormalizada(scheduledFor);
        acciones.reprogramar(id, version, nueva, motivo, usuarioActual(sesion))
                .ifPresent(problema -> flash.addFlashAttribute("error", problema));
        return "redirect:/pendientes/" + id;
    }

    @GetMapping("/pendientes/{id}/historial")
    public String historial(@PathVariable UUID id,
                            @RequestParam(defaultValue = "0") int page, Model modelo) {
        PendingTask t = pendientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("pendiente inexistente"));

        Paging pagina = Paging.of(page);
        var entradas = historial.deEntidad("PENDING_TASK", id, pagina);
        boolean hayMas = entradas.size() > pagina.size();
        if (hayMas) {
            entradas = entradas.subList(0, pagina.size());
        }

        modelo.addAttribute("pendiente", t);
        modelo.addAttribute("entradas", entradas);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        modelo.addAttribute("tituloPagina", "Historial de " + t.title());
        return "pending-tasks/history";
    }

    private PendingTaskForm desdePendiente(PendingTask t) {
        return new PendingTaskForm(t.title(), t.description(), t.pendingTaskTypeId(),
                t.priorityId(), t.pendingTaskStatusId(), t.judicialCaseId(),
                t.administrativeProcedureId(),
                texto(t.receivedAt()), texto(t.scheduledFor()), texto(t.deadline()),
                t.outputDocumentType(), t.outputDocumentNumber(), t.notes(), t.version());
    }

    private static String texto(LocalDate fecha) {
        return fecha == null ? null : fecha.toString();
    }

    /**
     * Lo programado para hoy y lo vencido que sigue activo (insumo, seccion 26).
     *
     * <p>Incluir lo vencido es deliberado: si solo mostrara los de hoy, lo que se
     * quedo atras desapareceria de la vista justo cuando mas importa mirarlo.
     */
    @GetMapping("/pendientes/hoy")
    public String hoy(@RequestParam(defaultValue = "0") int page, Model modelo) {
        LocalDate hoy = LocalDate.now(clock);
        Paging pagina = Paging.of(page);
        List<PendingTask> filas = pendientes.deHoy(hoy, pagina);

        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas = filas.subList(0, pagina.size());
        }

        poblarPlazos(modelo, filas, hoy);
        modelo.addAttribute("pendientes", filas);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("tituloPagina", "Pendientes de hoy");
        return "pending-tasks/today";
    }

    /** Historial de tareas cumplidas (insumo, seccion 32). */
    @GetMapping("/cumplidos")
    public String cumplidos(@RequestParam(defaultValue = "0") int page, Model modelo) {
        LocalDate hoy = LocalDate.now(clock);
        Paging pagina = Paging.of(page);
        List<PendingTask> filas = pendientes.cumplidos(pagina);

        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas = filas.subList(0, pagina.size());
        }

        // Ambos valores se calculan al consultar y se descartan. Las
        // reprogramaciones, con una sola agregacion para toda la pagina.
        var instantanea = calendario.paraListado(hoy);
        Map<UUID, Integer> tiempos = new LinkedHashMap<>();
        for (PendingTask t : filas) {
            if (t.receivedAt() != null && t.completedAt() != null) {
                LocalDate cumplido = LocalDate.ofInstant(t.completedAt(),
                        java.time.ZoneId.of("America/Lima"));
                plazos.diasHabilesTranscurridos(t.receivedAt(), cumplido, instantanea)
                        .ifPresent(dias -> tiempos.put(t.id(), dias));
            }
        }

        modelo.addAttribute("pendientes", filas);
        modelo.addAttribute("tiemposDeAtencion", tiempos);
        modelo.addAttribute("reprogramaciones",
                pendientes.reprogramacionesDe(filas.stream().map(PendingTask::id).toList()));
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        modelo.addAttribute("tituloPagina", "Tareas cumplidas");
        return "pending-tasks/completed";
    }
}
