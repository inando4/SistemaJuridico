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
import pe.org.beneficencia.legalcontrol.administrativestatus.AdministrativeStatusRepository;
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
    private final AdministrativeStatusRepository estados;
    private final AuditQueryRepository historial;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final Clock clock;

    public AdministrativeProcedureController(AdministrativeProcedureRepository procedimientos,
                                             AdministrativeProcedureService servicio,
                                             ProcedureAuthorization permisos,
                                             AdministrativeStatusRepository estados,
                                             AuditQueryRepository historial,
                                             CalendarRepository calendario,
                                             DeadlineEvaluator plazos, Clock clock) {
        this.procedimientos = procedimientos;
        this.servicio = servicio;
        this.permisos = permisos;
        this.estados = estados;
        this.historial = historial;
        this.calendario = calendario;
        this.plazos = plazos;
        this.clock = clock;
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
                    "Parametros de filtro no validos");
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
    public String formularioNuevo(Model modelo) {
        modelo.addAttribute("estados", estados.habilitados());
        modelo.addAttribute("form", AdministrativeProcedureForm.nuevo());
        modelo.addAttribute("errores", Map.of());
        modelo.addAttribute("tituloPagina", "Nuevo procedimiento administrativo");
        return "administrative-procedures/form";
    }

    @PostMapping("/administrativos")
    public String crear(@ModelAttribute AdministrativeProcedureForm form, HttpSession sesion,
                        Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesion");
        }

        var resultado = servicio.crear(form, actual.id());
        if (resultado.correcto()) {
            return "redirect:/administrativos/" + resultado.id();
        }

        // Se devuelve el formulario con lo que la persona escribio: no se pierde nada.
        modelo.addAttribute("estados", estados.habilitados());
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
        modelo.addAttribute("procedimiento", p);
        modelo.addAttribute("plazo", plazos.evaluar(p.deadline(), hoy, calendario.paraListado(hoy)));
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("puedeEditar",
                permisos.puedeEditar(usuarioActual(sesion), p.ownerId()));
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

        modelo.addAttribute("estados", estados.habilitados());
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
        modelo.addAttribute("estados", estados.habilitados());
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
