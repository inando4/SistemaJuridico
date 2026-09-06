package pe.org.beneficencia.legalcontrol.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Traduce los fallos a paginas en espanol, sin filtrar detalles internos.
 *
 * <p>El usuario ve que ocurrio y que puede hacer; la traza queda en el registro
 * del servidor. Nunca se muestra el mensaje de una excepcion tal cual: puede
 * contener nombres de tabla, consultas o datos de otra persona.
 */
@ControllerAdvice
public class ErrorHandling {

    /** Otra persona modifico el registro desde que se abrio el formulario (FR-013). */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ConflictoDeEdicion extends RuntimeException {
        public ConflictoDeEdicion(String mensaje) {
            super(mensaje);
        }
    }

    /** La operacion no esta permitida para este usuario sobre este registro (FR-010). */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinPermiso extends RuntimeException {
        public SinPermiso(String mensaje) {
            super(mensaje);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NoEncontrado extends RuntimeException {
        public NoEncontrado(String mensaje) {
            super(mensaje);
        }
    }

    @ExceptionHandler(ConflictoDeEdicion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflicto() {
        return "error/conflicto";
    }

    @ExceptionHandler(SinPermiso.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String sinPermiso() {
        return "error/sin-permiso";
    }

    @ExceptionHandler(NoEncontrado.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noEncontrado() {
        return "error/no-encontrado";
    }
}
