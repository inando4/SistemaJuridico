package pe.org.beneficencia.legalcontrol.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Pantalla de alertas (insumo, seccion 35).
 *
 * <p>La tarjeta del panel dice cuantos; esta pantalla dice cuales. Muestra los
 * pendientes de quien mira, cada uno con su nivel, en el orden de la seccion 24.
 */
@Controller
public class AlertController {

    private final AlertRepository alertas;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final Clock clock;

    public AlertController(AlertRepository alertas, CalendarRepository calendario,
                           DeadlineEvaluator plazos, Clock clock) {
        this.alertas = alertas;
        this.calendario = calendario;
        this.plazos = plazos;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/alertas")
    public String alertas(@RequestParam(defaultValue = "0") int page,
                          HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesion");
        }

        LocalDate hoy = LocalDate.now(clock);
        CalendarSnapshot instantanea = calendario.paraAntiguedad(hoy);

        Optional<LocalDate> frontera3 = plazos.sumarDiasHabiles(hoy,
                DashboardController.DIAS_PROXIMO_VENCIMIENTO, instantanea);
        Optional<LocalDate> hace15 = plazos.restarDiasHabiles(hoy,
                DeadlineEvaluator.UMBRAL_SIN_PLAZO, instantanea);

        Paging pagina = Paging.of(page);
        List<AlertRepository.PendienteConAlerta> filas = alertas.deResponsable(
                actual.id(), hoy, frontera3.orElse(null), hace15.orElse(null), pagina);

        boolean hayMas = filas.size() > pagina.size();
        if (hayMas) {
            filas = filas.subList(0, pagina.size());
        }

        modelo.addAttribute("alertas", filas);
        modelo.addAttribute("hayMas", hayMas);
        modelo.addAttribute("pagina", page);
        // Los niveles 4 y 5 no se pueden determinar sin calendario; los tres
        // primeros si, porque solo comparan fechas. Se avisa sin ocultarlos.
        modelo.addAttribute("faltaCalendario",
                frontera3.isEmpty() || hace15.isEmpty());
        modelo.addAttribute("fechaReferencia", hoy);
        modelo.addAttribute("tituloPagina", "Alertas");
        return "dashboard/alerts";
    }
}
