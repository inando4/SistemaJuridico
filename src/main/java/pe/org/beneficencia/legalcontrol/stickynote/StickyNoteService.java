package pe.org.beneficencia.legalcontrol.stickynote;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * Pegar y quitar recordatorios.
 *
 * <p>Siempre a nombre de quien llama: nadie pega ni quita notas de otro, tampoco
 * la jefa. La comprobacion vive en la consulta ({@code owner_id = :autor}), no en
 * ocultar el boton de la plantilla.
 */
@Service
public class StickyNoteService {

    public static final int MAXIMO_CARACTERES = 500;

    /**
     * Tope de notas pegadas por persona. El panel es la pantalla que mas se abre
     * (principio IV); un muro sin fin dejaria de ser un vistazo.
     */
    public static final int MAXIMO_PEGADAS = 30;

    private final StickyNoteRepository notas;
    private final Clock clock;

    public StickyNoteService(StickyNoteRepository notas, Clock clock) {
        this.notas = notas;
        this.clock = clock;
    }

    /** @return el motivo del rechazo, o vacio si se pego */
    @Transactional
    public Optional<String> pegar(String texto, String color, CuentaActual autor) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) {
            return Optional.of("Escriba el recordatorio antes de agregarlo.");
        }
        if (limpio.length() > MAXIMO_CARACTERES) {
            return Optional.of("El recordatorio no puede superar los "
                    + MAXIMO_CARACTERES + " caracteres.");
        }
        Optional<NoteColor> elegido = NoteColor.desde(color);
        if (elegido.isEmpty()) {
            return Optional.of("Elija uno de los colores de la lista.");
        }
        if (notas.pegadasDe(autor.id()) >= MAXIMO_PEGADAS) {
            return Optional.of("Ya tiene " + MAXIMO_PEGADAS
                    + " recordatorios. Quite alguno antes de agregar otro.");
        }
        notas.insertar(autor.id(), limpio, elegido.get(), clock.instant());
        return Optional.empty();
    }

    /** @throws ErrorHandling.NoEncontrado si no existe o es de otra persona */
    @Transactional
    public void quitar(UUID id, CuentaActual autor) {
        if (!notas.quitar(id, autor.id(), clock.instant())) {
            // Mismo mensaje para «no existe» y «es de otro»: no se revela cual.
            throw new ErrorHandling.NoEncontrado("recordatorio inexistente");
        }
    }
}
