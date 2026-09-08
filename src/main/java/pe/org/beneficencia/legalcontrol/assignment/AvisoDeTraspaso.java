package pe.org.beneficencia.legalcontrol.assignment;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Lo que se le dice a la jefa <b>antes</b> de reasignar.
 *
 * <p>Un expediente puede arrastrar pendientes que no son de su responsable: nada
 * impide registrar un pendiente colgado del expediente de otra persona. Al
 * reasignar, esos tambien viajan —es lo que significa que el expediente viaje
 * completo—, y eso le retira el acceso de edicion a un tercero que no ha
 * intervenido en la decision.
 *
 * <p><b>No puede pasar en silencio.</b> Esta clase arma la frase que lo dice, con
 * nombres, para que la jefa decida con el dato delante.
 */
@Component
public class AvisoDeTraspaso {

    private final JdbcClient jdbc;

    public AvisoDeTraspaso(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param vinculo    de que cuelgan los pendientes
     * @param expediente el registro que va a cambiar de manos
     * @param saliente   su responsable actual
     */
    public String para(ReassignmentRepository.Vinculo vinculo, UUID expediente, UUID saliente) {
        int total = contar(vinculo, expediente, null);
        if (total == 0) {
            return "Este expediente no tiene pendientes. Solo cambiará el responsable "
                    + "del expediente.";
        }

        List<String> terceros = nombresDeTerceros(vinculo, expediente, saliente);
        String base = "Se traspasarán " + total + " pendientes, incluidos los ya cumplidos.";
        if (terceros.isEmpty()) {
            return base;
        }
        int deTerceros = contar(vinculo, expediente, saliente);
        return base + " " + deTerceros + (deTerceros == 1 ? " es de otra persona (" : " son de otras personas (")
                + String.join(", ", terceros) + "), que "
                + (deTerceros == 1 ? "perderá" : "perderán") + " el acceso de edición.";
    }

    /** @param distintoDe si no es null, solo cuenta los que NO son de esa persona */
    private int contar(ReassignmentRepository.Vinculo vinculo, UUID expediente, UUID distintoDe) {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE %s = :expediente
                  AND (CAST(:distintoDe AS uuid) IS NULL OR owner_id <> CAST(:distintoDe AS uuid))
                """.formatted(vinculo.columna))
                .param("expediente", expediente).param("distintoDe", distintoDe)
                .query(Integer.class).single();
        return total == null ? 0 : total;
    }

    private List<String> nombresDeTerceros(ReassignmentRepository.Vinculo vinculo,
                                           UUID expediente, UUID saliente) {
        return jdbc.sql("""
                SELECT DISTINCT u.name
                FROM pending_task t JOIN app_user u ON u.id = t.owner_id
                WHERE t.%s = :expediente AND t.owner_id <> :saliente
                ORDER BY u.name
                """.formatted(vinculo.columna))
                .param("expediente", expediente).param("saliente", saliente)
                .query(String.class).list().stream().collect(Collectors.toList());
    }
}
