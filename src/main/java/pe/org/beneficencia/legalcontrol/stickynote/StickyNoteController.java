package pe.org.beneficencia.legalcontrol.stickynote;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Los recordatorios del panel. Solo escriben; se leen en el panel mismo.
 *
 * <p>Formularios normales con redireccion, como el resto del sistema: funcionan
 * sin JavaScript y una recarga no repite el envio.
 */
@Controller
public class StickyNoteController {

    /** Vuelta al panel, a la altura de los recordatorios. */
    private static final String AL_PANEL = "redirect:/#recordatorios";

    private final StickyNoteService servicio;

    public StickyNoteController(StickyNoteService servicio) {
        this.servicio = servicio;
    }

    private CuentaActual exigirSesion(HttpSession sesion) {
        CuentaActual cuenta = (CuentaActual) sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
        if (cuenta == null) {
            throw new ErrorHandling.SinPermiso("sin sesión");
        }
        return cuenta;
    }

    @PostMapping("/recordatorios")
    public String pegar(@RequestParam(defaultValue = "") String content,
                        @RequestParam(defaultValue = "YELLOW") String color,
                        HttpSession sesion, RedirectAttributes flash) {
        CuentaActual cuenta = exigirSesion(sesion);

        servicio.pegar(content, color, cuenta).ifPresent(motivo -> {
            // Texto y color vuelven al formulario: perder lo escrito por un rechazo
            // obliga a teclearlo otra vez.
            flash.addFlashAttribute("errorRecordatorio", motivo);
            flash.addFlashAttribute("textoRecordatorio", content);
            flash.addFlashAttribute("colorRecordatorio", color);
        });
        return AL_PANEL;
    }

    @PostMapping("/recordatorios/{id}/quitar")
    public String quitar(@PathVariable UUID id, HttpSession sesion) {
        servicio.quitar(id, exigirSesion(sesion));
        return AL_PANEL;
    }
}
