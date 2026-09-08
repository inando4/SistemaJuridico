package pe.org.beneficencia.legalcontrol.team;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
 * Vista de equipo (insumo, seccion 5.2).
 *
 * <p>Responde «¿como esta repartido el trabajo?», que es otra pregunta que la del
 * panel: aquel cuenta lo de quien mira y esta cuenta lo de todos. Por eso es una
 * pantalla aparte y no una pestaña.
 *
 * <p><b>La consulta es cualquiera</b>, no solo la jefatura: la lectura es
 * compartida (seccion 5.1). Lo que exige rol de jefa es reasignar, y eso se
 * comprueba en {@code ReassignmentService}.
 */
@Controller
public class TeamController {

    private final TeamWorkloadRepository cargas;
    private final CalendarRepository calendario;
    private final DeadlineEvaluator plazos;
    private final Clock clock;

    public TeamController(TeamWorkloadRepository cargas, CalendarRepository calendario,
                          DeadlineEvaluator plazos, Clock clock) {
        this.cargas = cargas;
        this.calendario = calendario;
        this.plazos = plazos;
        this.clock = clock;
    }

    @ModelAttribute("usuarioActual")
    public CuentaActual usuarioActual(HttpSession sesion) {
        return (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
    }

    @GetMapping("/equipo")
    public String equipo(HttpSession sesion, Model modelo) {
        if (usuarioActual(sesion) == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }

        LocalDate hoy = LocalDate.now(clock);
        LocalDate lunes = SemanaDeTrabajo.lunesDe(hoy);
        LocalDate domingo = SemanaDeTrabajo.domingoDe(hoy);

        // Hacia atras tambien: la antiguedad se cuenta desde la recepcion, que en
        // enero cae en el año anterior. Usar paraListado fue el fallo de la 003.
        CalendarSnapshot instantanea = calendario.paraAntiguedad(hoy);
        Optional<LocalDate> hace15 =
                plazos.restarDiasHabiles(hoy, DeadlineEvaluator.UMBRAL_SIN_PLAZO, instantanea);

        List<CargaDeAbogado> equipo = cargas
                .cargaDelEquipo(hoy, lunes, domingo, hace15.orElse(null))
                .stream()
                // El cero por falta de frontera no es un cero: se distingue aqui,
                // que es donde se sabe si la frontera existia.
                .map(f -> new CargaDeAbogado(f.id(), f.name(), f.role(),
                        f.vencidos(), f.estaSemana(),
                        hace15.isPresent() ? f.sinPlazoAntiguos() : null,
                        f.activos()))
                .toList();

        modelo.addAttribute("equipo", equipo);
        modelo.addAttribute("lunes", lunes);
        modelo.addAttribute("domingo", domingo);
        modelo.addAttribute("umbralSinPlazo", DeadlineEvaluator.UMBRAL_SIN_PLAZO);
        modelo.addAttribute("tituloPagina", "Carga del equipo");
        return "team/list";
    }
}
