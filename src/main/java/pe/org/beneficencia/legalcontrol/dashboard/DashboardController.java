package pe.org.beneficencia.legalcontrol.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.calendar.CalendarRepository;
import pe.org.beneficencia.legalcontrol.calendar.CalendarSnapshot;
import pe.org.beneficencia.legalcontrol.calendar.DeadlineEvaluator;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Pantalla de entrada (insumo, seccion 23).
 *
 * <p>Cuenta <b>lo de quien mira</b>, no lo del area. El sistema es de equipo y
 * todos pueden leer todo (seccion 5.1), pero ver no es contar: un dashboard que
 * sumara los cinco no le diria a nadie que hacer con su dia. La vista de conjunto
 * es la seccion 5.2, que es otra pantalla.
 */
@Controller
public class DashboardController {

    /** Dias habiles que definen «proximo vencimiento» (insumo, secciones 23 y 35). */
    public static final int DIAS_PROXIMO_VENCIMIENTO = 3;

    private final DashboardRepository resumenes;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final Clock clock;

    public DashboardController(DashboardRepository resumenes, CalendarRepository calendario,
                               DeadlineEvaluator plazos, Clock clock) {
        this.resumenes = resumenes;
        this.calendario = calendario;
        this.plazos = plazos;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/")
    public String dashboard(HttpSession sesion, Model modelo) {
        CuentaActual actual = usuarioActual(sesion);
        if (actual == null) {
            throw new ErrorHandling.SinPermiso("sin sesion");
        }

        LocalDate hoy = LocalDate.now(clock);

        // Una sola lectura del calendario para las dos fronteras. Tiene que mirar
        // tambien al ano anterior: la antiguedad se cuenta hacia atras desde la
        // recepcion, que en enero cae en el ano pasado.
        CalendarSnapshot instantanea = calendario.paraAntiguedad(hoy);

        Optional<LocalDate> frontera3 =
                plazos.sumarDiasHabiles(hoy, DIAS_PROXIMO_VENCIMIENTO, instantanea);
        Optional<LocalDate> hace15 =
                plazos.restarDiasHabiles(hoy, DeadlineEvaluator.UMBRAL_SIN_PLAZO, instantanea);

        var cuentas = resumenes.contar(actual.id(), hoy,
                frontera3.orElse(null), hace15.orElse(null),
                hoy.withDayOfMonth(1));

        // Un cero por falta de frontera no es un cero: se distingue aqui, que es
        // donde se sabe si la frontera existia.
        var resumen = new ResumenDelDia(
                cuentas.urgentesHoy(), cuentas.vencidos(),
                frontera3.isPresent() ? cuentas.proximos() : null,
                hace15.isPresent() ? cuentas.sinPlazo() : null,
                cuentas.activos(), cuentas.cumplidosMes());

        modelo.addAttribute("resumen", resumen);
        modelo.addAttribute("diasProximoVencimiento", DIAS_PROXIMO_VENCIMIENTO);
        modelo.addAttribute("umbralSinPlazo", DeadlineEvaluator.UMBRAL_SIN_PLAZO);
        modelo.addAttribute("tituloPagina", "Panel del dia");
        return "dashboard/index";
    }
}
