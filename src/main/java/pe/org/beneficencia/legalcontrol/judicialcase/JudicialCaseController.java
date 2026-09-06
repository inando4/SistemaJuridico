package pe.org.beneficencia.legalcontrol.judicialcase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;
import pe.org.beneficencia.legalcontrol.proceduralstatus.ProceduralStatusRepository;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Listado, alta y ficha de expedientes.
 *
 * <p>Todos los usuarios ven todos los expedientes, sean de quien sean: la
 * visibilidad compartida es del principio II y no depende del rol.
 */
@Controller
public class JudicialCaseController {

    private final JudicialCaseRepository expedientes;
    private final JudicialCaseService servicio;
    private final CaseAuthorization permisos;
    private final AuditQueryRepository historial;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final ProceduralStatusRepository estados;
    private final Clock clock;

    public JudicialCaseController(JudicialCaseRepository expedientes, JudicialCaseService servicio,
                                  CaseAuthorization permisos, AuditQueryRepository historial,
                                  CalendarRepository calendario, DeadlineEvaluator plazos,
                                  ProceduralStatusRepository estados, Clock clock) {
        this.expedientes = expedientes;
        this.servicio = servicio;
        this.permisos = permisos;
        this.historial = historial;
        this.calendario = calendario;
        this.plazos = plazos;
        this.estados = estados;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/judicial-cases")
    public String listado(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID proceduralStatusId,
            @RequestParam(required = false) String subject,
            @RequestParam(defaultValue = "any") String deadlinePresence,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "active") String visibility,
            @RequestParam(defaultValue = "caseNumber") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            Model modelo) {

        CaseFilters filtros = new CaseFilters(q, ownerId, proceduralStatusId, subject,
                deadlinePresence, overdue, visibility, sort, direction, page);

        if (!filtros.valido()) {
            // Un filtro invalido es error del cliente, no un listado vacio silencioso.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Parametros de filtro no validos");
        }

        LocalDate hoy = LocalDate.now(clock);
        Paging pagina = Paging.of(page);
        List<JudicialCase> filas = expedientes.listar(filtros, pagina, hoy);

        // La fila sobrante solo indica que hay pagina siguiente; no se muestra.
        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas = filas.subList(0, pagina.size());
        }

        var instantanea = calendario.paraListado(hoy);
        java.util.Map<UUID, DeadlineView> interpretaciones = new java.util.LinkedHashMap<>();
        for (JudicialCase e : filas) {
            interpretaciones.put(e.id(), plazos.evaluar(e.deadline(), hoy, instantanea));
        }

        modelo.addAttribute("expedientes", filas);
        modelo.addAttribute("plazos", interpretaciones);
        modelo.addAttribute("filtros", filtros);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("queryAnterior", filtros.comoQuery(Math.max(0, page - 1)));
        modelo.addAttribute("querySiguiente", filtros.comoQuery(page + 1));
        modelo.addAttribute("queryActual", filtros.comoQuery(page));
        modelo.addAttribute("tituloPagina", "Procesos judiciales");
        return "judicial-cases/list";
    }

    @GetMapping("/judicial-cases/new")
    public String formularioNuevo(Model modelo) {
        // Solo se ofrecen los habilitados: uno deshabilitado ya no es elegible.
        modelo.addAttribute("estados", estados.habilitados());
        modelo.addAttribute("form", JudicialCaseForm.nuevo());
        modelo.addAttribute("errores", java.util.Map.of());
        modelo.addAttribute("tituloPagina", "Nuevo proceso judicial");
        return "judicial-cases/form";
    }

    @PostMapping("/judicial-cases")
    public String crear(@ModelAttribute JudicialCaseForm form, HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesion");
        }

        var resultado = servicio.crear(form, actual.id());
        if (resultado.correcto()) {
            return "redirect:/judicial-cases/" + resultado.id();
        }

        // Se devuelve el formulario con lo que el usuario escribio: no se pierde nada.
        modelo.addAttribute("estados", estados.habilitados());
        modelo.addAttribute("form", form);
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Nuevo proceso judicial");
        return "judicial-cases/form";
    }

    @GetMapping("/judicial-cases/{id}")
    public String ficha(@PathVariable UUID id, Model modelo) {
        JudicialCase expediente = expedientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));
        LocalDate hoy = LocalDate.now(clock);
        modelo.addAttribute("expediente", expediente);
        modelo.addAttribute("plazo", plazos.evaluar(expediente.deadline(), hoy,
                calendario.paraListado(hoy)));
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("tituloPagina", "Expediente " + expediente.caseNumber());
        return "judicial-cases/detail";
    }

    @GetMapping("/judicial-cases/{id}/edit")
    public String formularioEdicion(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        JudicialCase e = expedientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));
        CuentaActual actual = usuarioActual(sesion);

        if (!permisos.puedeEditar(actual, e.ownerId())) {
            throw new ErrorHandling.SinPermiso("no puede editar expedientes ajenos");
        }

        modelo.addAttribute("estados", estados.habilitados());
        modelo.addAttribute("form", desdeExpediente(e));
        modelo.addAttribute("expediente", e);
        modelo.addAttribute("errores", java.util.Map.of());
        modelo.addAttribute("tituloPagina", "Editar " + e.caseNumber());
        return "judicial-cases/edit";
    }

    @PostMapping("/judicial-cases/{id}")
    public String editar(@PathVariable UUID id, @ModelAttribute JudicialCaseForm form,
                         HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        var resultado = servicio.editar(id, form, actual);

        if (resultado.correcto()) {
            return "redirect:/judicial-cases/" + id;
        }
        modelo.addAttribute("estados", estados.habilitados());
        modelo.addAttribute("form", form);
        modelo.addAttribute("expediente", expedientes.porId(id).orElseThrow());
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Editar expediente");
        return "judicial-cases/edit";
    }

    @PostMapping("/judicial-cases/{id}/visibility")
    public String visibilidad(@PathVariable UUID id,
                              @RequestParam boolean active,
                              @RequestParam long version,
                              HttpSession sesion) {
        servicio.cambiarVisibilidad(id, active, version, usuarioActual(sesion));
        return "redirect:/judicial-cases/" + id;
    }

    @GetMapping("/judicial-cases/{id}/history")
    public String historial(@PathVariable UUID id,
                            @RequestParam(defaultValue = "0") int page,
                            Model modelo) {
        JudicialCase e = expedientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));

        Paging pagina = Paging.of(page);
        var entradas = historial.deEntidad("JUDICIAL_CASE", id, pagina);
        boolean hayMas = entradas.size() > pagina.size();
        if (hayMas) {
            entradas = entradas.subList(0, pagina.size());
        }

        modelo.addAttribute("expediente", e);
        modelo.addAttribute("entradas", entradas);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        modelo.addAttribute("tituloPagina", "Historial de " + e.caseNumber());
        return "judicial-cases/history";
    }

    /** Rellena el formulario con lo que hay guardado, incluida la version actual. */
    private JudicialCaseForm desdeExpediente(JudicialCase e) {
        return new JudicialCaseForm(
                e.sequenceNumber() == null ? null : e.sequenceNumber().toString(),
                e.caseNumber(), e.claimant(), e.respondent(), e.subject(),
                e.proceduralStatusId(), e.lastProceduralAction(), e.nextProceduralAction(),
                e.lastActionDate() == null ? null : e.lastActionDate().toString(),
                e.deadline() == null ? null : e.deadline().toString(),
                e.amount() == null ? null : e.amount().toPlainString(),
                e.propertyAddress(), e.notes(), e.managementActions(),
                e.active(), e.version());
    }
}
