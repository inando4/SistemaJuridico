package pe.org.beneficencia.legalcontrol.administrativeprocedure;

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
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.assignment.AvisoDeTraspaso;
import pe.org.beneficencia.legalcontrol.assignment.DestinosDeAsignacion;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.pendingtask.PendientesDelExpediente;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineView;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Listado, alta, ficha e historial de procedimientos administrativos.
 *
 * <p>Rutas en espanol, como las fija el insumo (secciones 29 y 30).
 *
 * <p>Todos consultan todos los procedimientos, sean de quien sean: la visibilidad
 * compartida no depende del rol.
 */
@Controller
public class AdministrativeProcedureController {

    private final AdministrativeProcedureRepository procedimientos;
    private final AdministrativeProcedureService servicio;
    private final ProcedureAuthorization permisos;
    private final CatalogRepository catalogos;
    private final AuditQueryRepository historial;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final Clock clock;
    private final DestinosDeAsignacion destinos;
    private final AvisoDeTraspaso avisos;
    private final PendientesDelExpediente pendientesDelExpediente;

    public AdministrativeProcedureController(AdministrativeProcedureRepository procedimientos,
                                             AdministrativeProcedureService servicio,
                                             ProcedureAuthorization permisos,
                                             CatalogRepository catalogos,
                                             AuditQueryRepository historial,
                                             CalendarRepository calendario,
                                             DeadlineEvaluator plazos, Clock clock,
                                             DestinosDeAsignacion destinos,
                                             AvisoDeTraspaso avisos,
                                             PendientesDelExpediente pendientesDelExpediente) {
        this.procedimientos = procedimientos;
        this.servicio = servicio;
        this.permisos = permisos;
        this.catalogos = catalogos;
        this.historial = historial;
        this.calendario = calendario;
        this.plazos = plazos;
        this.clock = clock;
        this.destinos = destinos;
        this.avisos = avisos;
        this.pendientesDelExpediente = pendientesDelExpediente;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/administrativos")
    public String listado(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID administrativeStatusId,
            @RequestParam(required = false) String requestingArea,
            @RequestParam(defaultValue = "any") String deadlinePresence,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "active") String visibility,
            @RequestParam(defaultValue = "fileNumber") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            Model modelo) {

        ProcedureFilters filtros = new ProcedureFilters(q, ownerId, administrativeStatusId,
                requestingArea, deadlinePresence, overdue, visibility, sort, direction, page);

        if (!filtros.valido()) {
            // Un filtro invalido es error del cliente, no un listado vacio silencioso.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Parámetros de filtro no válidos");
        }

        LocalDate hoy = LocalDate.now(clock);
        Paging pagina = Paging.of(page);
        List<AdministrativeProcedure> filas = procedimientos.listar(filtros, pagina, hoy);

        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas = filas.subList(0, pagina.size());
        }

        // Una sola lectura del calendario para todas las filas de la pagina.
        var instantanea = calendario.paraListado(hoy);
        Map<UUID, DeadlineView> interpretaciones = new LinkedHashMap<>();
        for (AdministrativeProcedure p : filas) {
            interpretaciones.put(p.id(), plazos.evaluar(p.deadline(), hoy, instantanea));
        }

        modelo.addAttribute("procedimientos", filas);
        modelo.addAttribute("plazos", interpretaciones);
        modelo.addAttribute("filtros", filtros);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("queryAnterior", filtros.comoQuery(Math.max(0, page - 1)));
        modelo.addAttribute("querySiguiente", filtros.comoQuery(page + 1));
        modelo.addAttribute("tituloPagina", "Procedimientos administrativos");
        return "administrative-procedures/list";
    }

    @GetMapping("/administrativos/nuevo")
    public String formularioNuevo(HttpSession sesion, Model modelo) {
        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_ADMINISTRATIVOS));
        modelo.addAttribute("form", AdministrativeProcedureForm.nuevo());
        // El desplegable de responsable solo para la jefatura (RF-012, RF-013).
        if (usuarioActual(sesion) != null && usuarioActual(sesion).esJefa()) {
            modelo.addAttribute("candidatosAResponsable", destinos.activos(null));
        }
        modelo.addAttribute("errores", Map.of());
        modelo.addAttribute("tituloPagina", "Nuevo procedimiento administrativo");
        return "administrative-procedures/form";
    }

    @PostMapping("/administrativos")
    public String crear(@ModelAttribute AdministrativeProcedureForm form,
                        @org.springframework.web.bind.annotation.RequestParam(required = false)
                        UUID ownerId,
                        HttpSession sesion,
                        Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }

        // Solo la jefatura elige responsable; un ownerId de un abogado se ignora.
        UUID responsable = actual.esJefa() && ownerId != null && destinos.puedeRecibir(ownerId)
                ? ownerId
                : actual.id();
        var resultado = servicio.crear(form, responsable, actual.id());
        if (resultado.correcto()) {
            return "redirect:/administrativos/" + resultado.id();
        }

        // Se devuelve el formulario con lo que la persona escribio: no se pierde nada.
        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_ADMINISTRATIVOS));
        modelo.addAttribute("form", form);
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Nuevo procedimiento administrativo");
        return "administrative-procedures/form";
    }

    @GetMapping("/administrativos/{id}")
    public String ficha(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        AdministrativeProcedure p = procedimientos.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("procedimiento inexistente"));

        LocalDate hoy = LocalDate.now(clock);

        // Igual que en la ficha judicial: una instantanea para el plazo del
        // procedimiento y para los de sus pendientes, y de las que cubren el ano
        // anterior, para que un plazo vencido en diciembre no parezca sin calendario
        // al mirarlo en enero.
        CalendarSnapshot instantanea = calendario.paraAntiguedad(hoy);

        modelo.addAttribute("procedimiento", p);
        modelo.addAttribute("plazo", plazos.evaluar(p.deadline(), hoy, instantanea));
        modelo.addAttribute("fechaReferencia", hoy);

        // El bloque de la seccion 30 del insumo, que la 002 dejo apuntado como hueco.
        pendientesDelExpediente.poblarAdministrativo(modelo, id, hoy, instantanea);
        modelo.addAttribute("puedeEditar",
                permisos.puedeEditar(usuarioActual(sesion), p.ownerId()));

        // Igual que en la ficha judicial: solo para quien puede reasignar.
        if (usuarioActual(sesion) != null && usuarioActual(sesion).esJefa()) {
            modelo.addAttribute("destinoReasignacion", "/administrativos/" + id + "/responsable");
            modelo.addAttribute("candidatosAReasignar", destinos.activos(p.ownerId()));
            modelo.addAttribute("avisoDeTraspaso", avisos.para(
                    ReassignmentRepository.Vinculo.ADMINISTRATIVO, id, p.ownerId()));
        }
        modelo.addAttribute("tituloPagina", "Procedimiento " + p.fileNumber());
        return "administrative-procedures/detail";
    }

    @GetMapping("/administrativos/{id}/editar")
    public String formularioEdicion(@PathVariable UUID id, HttpSession sesion, Model modelo) {
        AdministrativeProcedure p = procedimientos.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("procedimiento inexistente"));

        if (!permisos.puedeEditar(usuarioActual(sesion), p.ownerId())) {
            throw new ErrorHandling.SinPermiso("no puede editar procedimientos ajenos");
        }

        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_ADMINISTRATIVOS));
        modelo.addAttribute("form", desdeProcedimiento(p));
        modelo.addAttribute("procedimiento", p);
        modelo.addAttribute("errores", Map.of());
        modelo.addAttribute("tituloPagina", "Editar " + p.fileNumber());
        return "administrative-procedures/edit";
    }

    @PostMapping("/administrativos/{id}")
    public String editar(@PathVariable UUID id, @ModelAttribute AdministrativeProcedureForm form,
                         HttpSession sesion, Model modelo) {
        var resultado = servicio.editar(id, form, usuarioActual(sesion));

        if (resultado.correcto()) {
            return "redirect:/administrativos/" + id;
        }
        modelo.addAttribute("estados", catalogos.habilitados(CatalogDefinition.ESTADOS_ADMINISTRATIVOS));
        modelo.addAttribute("form", form);
        modelo.addAttribute("procedimiento", procedimientos.porId(id).orElseThrow());
        modelo.addAttribute("errores", resultado.errores());
        modelo.addAttribute("tituloPagina", "Editar procedimiento");
        return "administrative-procedures/edit";
    }

    @PostMapping("/administrativos/{id}/visibilidad")
    public String visibilidad(@PathVariable UUID id, @RequestParam boolean active,
                              @RequestParam long version, HttpSession sesion) {
        servicio.cambiarVisibilidad(id, active, version, usuarioActual(sesion));
        return "redirect:/administrativos/" + id;
    }

    @GetMapping("/administrativos/{id}/historial")
    public String historial(@PathVariable UUID id,
                            @RequestParam(defaultValue = "0") int page, Model modelo) {
        AdministrativeProcedure p = procedimientos.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("procedimiento inexistente"));

        Paging pagina = Paging.of(page);
        var entradas = historial.deEntidad("ADMINISTRATIVE_PROCEDURE", id, pagina);
        boolean hayMas = entradas.size() > pagina.size();
        if (hayMas) {
            entradas = entradas.subList(0, pagina.size());
        }

        modelo.addAttribute("procedimiento", p);
        modelo.addAttribute("entradas", entradas);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        modelo.addAttribute("tituloPagina", "Historial de " + p.fileNumber());
        return "administrative-procedures/history";
    }

    /** Rellena el formulario con lo guardado, incluida la version actual. */
    private AdministrativeProcedureForm desdeProcedimiento(AdministrativeProcedure p) {
        return new AdministrativeProcedureForm(
                p.sequenceNumber() == null ? null : p.sequenceNumber().toString(),
                p.fileNumber(), p.requestingArea(), p.request(), p.administrativeStatusId(),
                p.receivedAt() == null ? null : p.receivedAt().toString(),
                p.deadline() == null ? null : p.deadline().toString(),
                p.notes(), p.active(), p.version());
    }
}
