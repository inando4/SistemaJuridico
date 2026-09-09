package pe.org.beneficencia.legalcontrol.agenda;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.DestinosDeAsignacion;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.config.ClockConfig;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * El calendario (insumo, seccion 31).
 *
 * <p>El rango lo deriva el servidor de la vista y de un <b>ancla</b>, no se recibe
 * como {@code desde}/{@code hasta}. Asi nadie puede pedir tres años de golpe y disparar
 * el coste de una pantalla que no pagina.
 */
@Controller
public class AgendaController {

    private final AgendaRepository agenda;
    private final CalendarRepository calendario;
    private final DestinosDeAsignacion personas;
    private final Clock clock;

    public AgendaController(AgendaRepository agenda, CalendarRepository calendario,
                            DestinosDeAsignacion personas, Clock clock) {
        this.agenda = agenda;
        this.calendario = calendario;
        this.personas = personas;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/calendario")
    public String calendario(@RequestParam(required = false) String vista,
                             @RequestParam(required = false) String ancla,
                             @RequestParam(required = false) UUID ownerId,
                             @RequestParam(defaultValue = "false") boolean todos,
                             HttpSession sesion, Model modelo) {
        CuentaActual cuenta = usuarioActual(sesion);
        if (cuenta == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }

        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ClockConfig.ZONA);
        String vistaElegida = RejillaDelMes.vistaValida(vista);
        LocalDate anclaElegida = fecha(ancla, hoy);

        LocalDate desde = RejillaDelMes.desde(vistaElegida, anclaElegida);
        LocalDate hasta = RejillaDelMes.hasta(vistaElegida, anclaElegida);

        // Toda el area cuando se pide explicitamente; si no, lo propio. Es una
        // comodidad de lectura, no un permiso: la lectura es compartida (principio II).
        UUID persona = todos ? null : (ownerId != null ? ownerId : cuenta.id());

        List<EventoDeAgenda> eventos = agenda.eventos(desde, hasta, persona);

        // Los años los da EL RANGO QUE SE PINTA, nunca «hoy». paraListado(hoy) arranca
        // en el año en curso, asi que navegar a un mes de un año anterior lo dejaria sin
        // sombreado y avisando en falso. Es el fallo que la 003 dejo en produccion.
        CalendarSnapshot instantanea = calendario.instantanea(desde.getYear(), hasta.getYear());
        boolean cubierto = instantanea.cubre(desde.getYear()) && instantanea.cubre(hasta.getYear());

        modelo.addAttribute("vista", vistaElegida);
        modelo.addAttribute("ancla", anclaElegida);
        modelo.addAttribute("desde", desde);
        modelo.addAttribute("hasta", hasta);
        modelo.addAttribute("hoy", hoy);
        modelo.addAttribute("eventosPorDia", RejillaDelMes.porDia(eventos));
        modelo.addAttribute("semanas", RejillaDelMes.semanas(desde, hasta));
        modelo.addAttribute("dias", RejillaDelMes.dias(desde, hasta));
        modelo.addAttribute("totalEventos", eventos.size());
        modelo.addAttribute("mesEnCurso", anclaElegida.getMonth());
        modelo.addAttribute("anclaAnterior", RejillaDelMes.anterior(vistaElegida, anclaElegida));
        modelo.addAttribute("anclaSiguiente", RejillaDelMes.siguiente(vistaElegida, anclaElegida));
        modelo.addAttribute("ownerId", persona);
        modelo.addAttribute("todos", todos);
        modelo.addAttribute("personas", personas.activos(null));
        modelo.addAttribute("diasNoLaborables", instantanea.diasNoLaborables());
        modelo.addAttribute("calendarioCubierto", cubierto);
        modelo.addAttribute("tituloPagina", "Calendario");
        return "agenda/calendar";
    }

    /** Un ancla ilegible no es un error: se usa hoy. */
    private LocalDate fecha(String valor, LocalDate hoy) {
        if (valor == null || valor.isBlank()) {
            return hoy;
        }
        try {
            return LocalDate.parse(valor.strip());
        } catch (java.time.format.DateTimeParseException e) {
            return hoy;
        }
    }
}
