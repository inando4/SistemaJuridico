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
import pe.org.beneficencia.legalcontrol.assignment.AvisoDeTraspaso;
import pe.org.beneficencia.legalcontrol.assignment.DestinosDeAsignacion;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.pendingtask.PendientesDelExpediente;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
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
    private final CatalogRepository catalogos;
    private final Clock clock;
    private final DestinosDeAsignacion destinos;
    private final AvisoDeTraspaso avisos;
    private final PendientesDelExpediente pendientesDelExpediente;

    public JudicialCaseController(JudicialCaseRepository expedientes, JudicialCaseService servicio,
                                  CaseAuthorization permisos, AuditQueryRepository historial,
                                  CalendarRepository calendario, DeadlineEvaluator plazos,
                                  CatalogRepository catalogos, Clock clock,
                                  DestinosDeAsignacion destinos, AvisoDeTraspaso avisos,
                                  PendientesDelExpediente pendientesDelExpediente) {
        this.expedientes = expedientes;
        this.servicio = servicio;
        this.permisos = permisos;
        this.historial = historial;
        this.calendario = calendario;
        this.plazos = plazos;
        this.catalogos = catalogos;
        this.clock = clock;
        this.destinos = destinos;
        this.avisos = avisos;
        this.pendientesDelExpediente = pendientesDelExpediente;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/judiciales")
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
                    "Parámetros de filtro no válidos");
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

    @GetMapping("/judiciales/nuevo")
    public String formularioNuevo(HttpSession sesion, Model modelo) {
        // Solo se ofrecen los habilitados: uno deshabilitado ya no es elegible.
        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_PROCESALES));
        modelo.addAttribute("form", JudicialCaseForm.nuevo());
        // El desplegable de responsable solo para la jefatura (RF-012, RF-013).
        if (usuarioActual(sesion) != null && usuarioActual(sesion).esJefa()) {
            modelo.addAttribute("candidatosAResponsable", destinos.activos(null));
        }
        modelo.addAttribute("errores", java.util.Map.of());
        modelo.addAttribute("tituloPagina", "Nuevo proceso judicial");
        return "judicial-cases/form";
    }

    @PostMapping("/judiciales")
    public String crear(@ModelAttribute JudicialCaseForm form,
                        @org.springframework.web.bind.annotation.RequestParam(required = false)
                        UUID ownerId,
                        HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }

        // Solo la jefatura puede elegir responsable. Si un abogado envia ownerId,
        // el servidor lo ignora y usa su propia identidad: la autorizacion no
        // depende de lo que el formulario muestre (principio II).
        UUID responsable = actual.esJefa() && ownerId != null && destinos.puedeRecibir(ownerId)
                ? ownerId
                : actual.id();
        var resultado = servicio.crear(form, responsable, actual.id());
        if (resultado.correcto()) {
            return "redirect:/judiciales/" + resultado.id();
        }

        // Se devuelve el formulario con lo que el usuario escribio: no se pierde nada.
        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_PROCESALES));
        modelo.addAttribute("form", form);
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Nuevo proceso judicial");
        return "judicial-cases/form";
    }

    @GetMapping("/judiciales/{id}")
    public String ficha(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        JudicialCase expediente = expedientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));
        LocalDate hoy = LocalDate.now(clock);

        // Una sola instantanea para el plazo del expediente y para los de sus
        // pendientes. Pedir dos seria una consulta mas por nada.
        //
        // paraAntiguedad y no paraListado porque cubre tambien el ano pasado: un
        // plazo vencido en diciembre, mirado en enero, caia fuera de la de listado
        // y se reportaba como «sin calendario» teniendolo completo.
        CalendarSnapshot instantanea = calendario.paraAntiguedad(hoy);

        modelo.addAttribute("expediente", expediente);
        modelo.addAttribute("plazo", plazos.evaluar(expediente.deadline(), hoy, instantanea));
        modelo.addAttribute("fechaReferencia", hoy);
        // Quien puede editar puede tambien ocultar: es la misma regla que aplica
        // CaseAuthorization en el servidor —responsable o jefa—, y la ficha tiene que
        // pintar exactamente eso. Con esJefa() se le esconderia el control a la
        // responsable del expediente, a quien el servidor si se lo permite.
        modelo.addAttribute("puedeEditar",
                permisos.puedeEditar(usuarioActual(sesion), expediente.ownerId()));

        // El bloque de la seccion 28 del insumo. No consulta por su cuenta: pide la
        // lista por el mismo camino que el listado general.
        pendientesDelExpediente.poblarJudicial(modelo, id, hoy, instantanea);

        // Lo de la reasignacion solo se calcula para quien puede hacerla: al resto
        // no se le pinta el formulario y estas consultas serian trabajo tirado.
        if (usuarioActual(sesion) != null && usuarioActual(sesion).esJefa()) {
            modelo.addAttribute("destinoReasignacion", "/judiciales/" + id + "/responsable");
            modelo.addAttribute("candidatosAReasignar", destinos.activos(expediente.ownerId()));
            modelo.addAttribute("avisoDeTraspaso", avisos.para(
                    ReassignmentRepository.Vinculo.JUDICIAL, id, expediente.ownerId()));
        }
        modelo.addAttribute("tituloPagina", "Expediente " + expediente.caseNumber());
        return "judicial-cases/detail";
    }

    @GetMapping("/judiciales/{id}/editar")
    public String formularioEdicion(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        JudicialCase e = expedientes.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("expediente inexistente"));
        CuentaActual actual = usuarioActual(sesion);

        if (!permisos.puedeEditar(actual, e.ownerId())) {
            throw new ErrorHandling.SinPermiso("no puede editar expedientes ajenos");
        }

        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_PROCESALES));
        modelo.addAttribute("form", desdeExpediente(e));
        modelo.addAttribute("expediente", e);
        modelo.addAttribute("errores", java.util.Map.of());
        modelo.addAttribute("tituloPagina", "Editar " + e.caseNumber());
        return "judicial-cases/edit";
    }

    @PostMapping("/judiciales/{id}")
    public String editar(@PathVariable UUID id, @ModelAttribute JudicialCaseForm form,
                         HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        var resultado = servicio.editar(id, form, actual);

        if (resultado.correcto()) {
            return "redirect:/judiciales/" + id;
        }
        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_PROCESALES));
        modelo.addAttribute("form", form);
        modelo.addAttribute("expediente", expedientes.porId(id).orElseThrow());
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Editar expediente");
        return "judicial-cases/edit";
    }

    @PostMapping("/judiciales/{id}/visibilidad")
    public String visibilidad(@PathVariable UUID id,
                              @RequestParam boolean active,
                              @RequestParam long version,
                              HttpSession sesion) {
        servicio.cambiarVisibilidad(id, active, version, usuarioActual(sesion));
        return "redirect:/judiciales/" + id;
    }

    @GetMapping("/judiciales/{id}/historial")
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
                e.propertyAddress(), e.notes(), e.managementActions(), e.version());
    }
}
