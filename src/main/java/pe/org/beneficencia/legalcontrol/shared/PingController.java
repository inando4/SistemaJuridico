package pe.org.beneficencia.legalcontrol.shared;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Comprobacion ligera de disponibilidad del proceso, sin consultar la base. */
@RestController
public class PingController {
    @GetMapping(value = "/ping", produces = "text/plain")
    public String ping() {
        return "pong";
    }
}
