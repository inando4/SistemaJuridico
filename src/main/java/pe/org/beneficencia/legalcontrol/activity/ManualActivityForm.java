package pe.org.beneficencia.legalcontrol.activity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que se rellena al registrar una actividad manual.
 *
 * @param description  que se hizo; obligatorio
 * @param performedOn  el dia en que se hizo; obligatorio y nunca futuro
 * @param typeId       tipo del catalogo, o nulo
 * @param otherType    tipo escrito a mano cuando ninguno del catalogo encaja, o nulo
 * @param version      para el bloqueo optimista al editar; nulo al crear
 */
public record ManualActivityForm(
        String description,
        LocalDate performedOn,
        UUID typeId,
        String otherType,
        Long version) {

    /** El texto libre vacio es «sin tipo», no una cadena en blanco. */
    public String otroTipoNormalizado() {
        if (typeId != null || otherType == null || otherType.isBlank()) {
            return null;
        }
        return otherType.strip();
    }
}
