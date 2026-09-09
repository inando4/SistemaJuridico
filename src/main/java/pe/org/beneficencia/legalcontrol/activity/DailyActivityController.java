package pe.org.beneficencia.legalcontrol.activity;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.DestinosDeAsignacion;
import pe.org.beneficencia.legalcontrol.catalog.CatalogDefinition;
import pe.org.beneficencia.legalcontrol.catalog.CatalogRepository;
import pe.org.beneficencia.legalcontrol.config.ClockConfig;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskRepository;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * «¿Que hice hoy?» (insumo, seccion 33).
 *
 * <p>La lectura es de todos (seccion 5.1): se puede consultar la actividad de
 * cualquier persona activa. Lo que no se puede es <b>registrar por otro</b>: el
 * formulario de alta solo sale cuando se mira la actividad propia, y el servicio
 * escribe siempre a nombre de quien lo llama.
 */
@Controller
public class DailyActivityController {

    private final ManualActivityRepository actividades;
    private final ManualActivityService servicio;
    private final PendingTaskRepository pendientes;
    private final CatalogRepository catalogos;
    private final DestinosDeAsignacion personas;
    private final AuditQueryRepository historial;
    private final Clock clock;

    public DailyActivityController(ManualActivityRepository actividades,
                                   ManualActivityService servicio,
                                   PendingTaskRepository pendientes,
                                   CatalogRepository catalogos,
                                   DestinosDeAsignacion personas,
                                   AuditQueryRepository historial, Clock clock) {
        this.actividades = actividades;
        this.servicio = servicio;
        this.pendientes = pendientes;
        this.catalogos = catalogos;
        this.personas = personas;
        this.historial = historial;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    private CuentaActual exigirSesion(HttpSession sesion) {
        CuentaActual cuenta = usuarioActual(sesion);
        if (cuenta == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }
        return cuenta;
    }

    @GetMapping("/actividad-diaria")
    public String actividad(@RequestParam(required = false) String dia,
                            @RequestParam(required = false) UUID ownerId,
                            HttpSession sesion, Model modelo) {
        CuentaActual cuenta = exigirSesion(sesion);
        LocalDate hoy = servicio.hoy();
        LocalDate consultado = diaPedido(dia, hoy);
        UUID persona = ownerId != null ? ownerId : cuenta.id();

        // La frontera del dia se calcula aqui, en la zona del area, y llega a SQL
        // como dos instantes. Castear completed_at a date lo resolveria en la zona
        // del servidor y mandaria lo cumplido de noche al dia siguiente.
        var inicio = consultado.atStartOfDay(ClockConfig.ZONA).toInstant();
        var fin = consultado.plusDays(1).atStartOfDay(ClockConfig.ZONA).toInstant();

        var actividad = new ActividadDelDia(consultado,
                pendientes.cumplidosEnElDia(persona, inicio, fin),
                actividades.delDia(persona, consultado));

        modelo.addAttribute("actividad", actividad);
        modelo.addAttribute("dia", consultado);
        modelo.addAttribute("diaAnterior", consultado.minusDays(1));
        modelo.addAttribute("diaSiguiente", consultado.plusDays(1));
        modelo.addAttribute("hayDiaSiguiente", consultado.isBefore(hoy));
        modelo.addAttribute("hoy", hoy);
        modelo.addAttribute("ownerId", persona);
        modelo.addAttribute("esPropia", persona.equals(cuenta.id()));
        modelo.addAttribute("personas", personas.activos(null));
        modelo.addAttribute("tipos", catalogos.habilitados(CatalogDefinition.TIPOS_DE_PENDIENTE));
        modelo.addAttribute("tituloPagina", "Actividad diaria");
        if (!modelo.containsAttribute("form")) {
            modelo.addAttribute("form",
                    new ManualActivityForm(null, consultado, null, null, null));
        }
        modelo.addAttribute("errores", modelo.asMap().getOrDefault("errores", Map.of()));
        return "activity/day";
    }

    /** Un dia ilegible o futuro no es un error: se muestra hoy. */
    private LocalDate diaPedido(String dia, LocalDate hoy) {
        if (dia == null || dia.isBlank()) {
            return hoy;
        }
        try {
            LocalDate pedido = LocalDate.parse(dia.strip());
            return pedido.isAfter(hoy) ? hoy : pedido;
        } catch (java.time.format.DateTimeParseException e) {
            return hoy;
        }
    }

    @PostMapping("/actividad-diaria")
    public String registrar(@ModelAttribute ManualActivityForm form,
                            HttpSession sesion, Model modelo, RedirectAttributes flash) {
        CuentaActual cuenta = exigirSesion(sesion);

        Map<String, String> errores = servicio.validar(form);
        if (!errores.isEmpty()) {
            modelo.addAttribute("errores", errores);
            modelo.addAttribute("form", form);
            return actividad(String.valueOf(form.performedOn()), cuenta.id(), sesion, modelo);
        }

        servicio.crear(form, cuenta);
        flash.addFlashAttribute("mensaje", "Actividad registrada.");
        return "redirect:/actividad-diaria?dia=" + form.performedOn();
    }

    @PostMapping("/actividad-diaria/{id}/editar")
    public String editar(@PathVariable UUID id, @ModelAttribute ManualActivityForm form,
                         HttpSession sesion, Model modelo, RedirectAttributes flash) {
        CuentaActual cuenta = exigirSesion(sesion);

        Map<String, String> errores = servicio.validar(form);
        if (!errores.isEmpty()) {
            modelo.addAttribute("errores", errores);
            modelo.addAttribute("form", form);
            return actividad(String.valueOf(form.performedOn()), cuenta.id(), sesion, modelo);
        }

        servicio.editar(id, form, version(form), cuenta);
        flash.addFlashAttribute("mensaje", "Actividad corregida.");
        return "redirect:/actividad-diaria?dia=" + form.performedOn();
    }

    @PostMapping("/actividad-diaria/{id}/retirar")
    public String retirar(@PathVariable UUID id, @RequestParam long version,
                          @RequestParam(required = false) String dia,
                          HttpSession sesion, RedirectAttributes flash) {
        CuentaActual cuenta = exigirSesion(sesion);

        servicio.retirar(id, version, cuenta);
        flash.addFlashAttribute("mensaje", "Actividad retirada. Sigue en el historial.");
        return "redirect:/actividad-diaria" + (dia == null || dia.isBlank() ? "" : "?dia=" + dia);
    }

    @GetMapping("/actividad-diaria/{id}/historial")
    public String historial(@PathVariable UUID id,
                            @RequestParam(defaultValue = "0") int page,
                            HttpSession sesion, Model modelo) {
        exigirSesion(sesion);

        ManualActivity actividad = actividades.porId(id)
                .orElseThrow(() -> new ErrorHandling.NoEncontrado("actividad inexistente"));

        Paging pagina = Paging.of(page);
        var entradas = historial.deEntidad(ManualActivityService.ENTIDAD, id, pagina);
        boolean hayMas = entradas.size() > pagina.size();
        if (hayMas) {
            entradas = entradas.subList(0, pagina.size());
        }

        modelo.addAttribute("actividad", actividad);
        modelo.addAttribute("entradas", entradas);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        modelo.addAttribute("tituloPagina", "Historial de la actividad");
        return "activity/history";
    }

    private long version(ManualActivityForm form) {
        if (form.version() == null) {
            throw new ErrorHandling.ConflictoDeEdicion("falta la versión del registro");
        }
        return form.version();
    }
}
