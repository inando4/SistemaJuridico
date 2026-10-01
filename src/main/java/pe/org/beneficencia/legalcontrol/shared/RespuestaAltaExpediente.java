package pe.org.beneficencia.legalcontrol.shared;

import java.util.Map;
import java.util.UUID;

/** Resultado del alta desde un pendiente, sin exponer la ficha completa. */
public record RespuestaAltaExpediente(
        UUID id, String numero, Map<String, String> errores, String advertencia) {
}
