package pe.org.beneficencia.legalcontrol.search;

import java.util.UUID;

/**
 * La referencia minima a un registro que coincide.
 *
 * <p>Solo lo justo para reconocerlo y abrirlo. No es la ficha: traer las veinte
 * columnas de un expediente para pintar dos lineas seria pagar por lo que nadie mira,
 * y quien reconoce el suyo lo abre.
 *
 * @param id           del registro, para el enlace
 * @param titulo       lo que identifica el registro: numero de expediente, o titulo
 * @param descripcion  una segunda linea que ayuda a distinguirlo de otro parecido
 * @param responsable  de quien es, porque «¿quien lleva ese caso?» es la otra mitad
 *                     de la pregunta que trae a alguien al buscador
 * @param activo       false si esta archivado o dado de baja; la vista lo señala
 */
public record ResultadoDeBusqueda(
        UUID id,
        String titulo,
        String descripcion,
        String responsable,
        boolean activo) {
}
