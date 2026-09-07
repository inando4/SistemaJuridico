package pe.org.beneficencia.legalcontrol.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Administracion del calendario de dias no laborables.
 *
 * <p>Consultarlo lo puede hacer cualquiera: todos necesitan entender por que un
 * plazo cuenta lo que cuenta. Modificarlo y confirmar la cobertura, solo JEFA.
 */
@Controller
public class CalendarController {

    private final NonWorkingDayRepository dias;
    private final CalendarService calendario;
    private final CalendarReviewService revisiones;
    private final Clock clock;

    public CalendarController(NonWorkingDayRepository dias, CalendarService calendario,
                              CalendarReviewService revisiones, Clock clock) {
        this.dias = dias;
        this.calendario = calendario;
        this.revisiones = revisiones;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    private CuentaActual exigirJefa(HttpSession sesion) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null || !actual.esJefa()) {
            throw new ErrorHandling.SinPermiso("solo la jefatura administra el calendario");
        }
        return actual;
    }

    @GetMapping("/dias-no-laborables")
    public String listado(@RequestParam(required = false) Integer year,
                          HttpSession sesion, Model modelo) {
        int ano = year == null ? LocalDate.now(clock).getYear() : year;

        modelo.addAttribute("ano", ano);
        modelo.addAttribute("dias", dias.delAno(ano));
        modelo.addAttribute("cantidad", dias.contarDelAno(ano));
        modelo.addAttribute("cubierto", calendario.cubierto(ano));
        modelo.addAttribute("revision", dias.revisionActual(ano).orElse(0L));
        modelo.addAttribute("tipos", Tipo.values());
        modelo.addAttribute("umbralBajo", CalendarReviewService.UMBRAL_CANTIDAD_BAJA);
        modelo.addAttribute("tituloPagina", "Dias no laborables de " + ano);
        return "calendar/list";
    }

    @PostMapping("/dias-no-laborables")
    public String agregar(@RequestParam String day, @RequestParam String description,
                          @RequestParam String kind, HttpSession sesion,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        CuentaActual jefa = exigirJefa(sesion);
        LocalDate dia;
        try {
            dia = LocalDate.parse(day);
        } catch (Exception e) {
            flash.addFlashAttribute("error", "La fecha no es valida.");
            return "redirect:/dias-no-laborables";
        }
        calendario.agregar(dia, description, Tipo.valueOf(kind), jefa)
                .ifPresent(motivo -> flash.addFlashAttribute("error", motivo));
        return "redirect:/dias-no-laborables?year=" + dia.getYear();
    }

    @PostMapping("/dias-no-laborables/{id}/eliminar")
    public String retirar(@PathVariable UUID id, @RequestParam long version,
                          @RequestParam int year, HttpSession sesion) {
        calendario.retirar(id, version, exigirJefa(sesion));
        return "redirect:/dias-no-laborables?year=" + year;
    }

    @PostMapping("/dias-no-laborables/{year}/revision")
    public String confirmar(@PathVariable int year,
                            @RequestParam long revision,
                            @RequestParam(defaultValue = "false") boolean fullYearReviewed,
                            @RequestParam(defaultValue = "false") boolean lowCountAcknowledged,
                            HttpSession sesion,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        CuentaActual jefa = exigirJefa(sesion);
        revisiones.confirmar(year, revision, fullYearReviewed, lowCountAcknowledged, jefa)
                .ifPresentOrElse(
                        motivo -> flash.addFlashAttribute("error", motivo),
                        () -> flash.addFlashAttribute("aviso",
                                "Cobertura de " + year + " confirmada."));
        return "redirect:/dias-no-laborables?year=" + year;
    }
}
