package pe.org.beneficencia.legalcontrol.assignment;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A quien se le puede pasar trabajo.
 *
 * <p><b>El criterio es la cuenta activa, no el rol.</b> La jefa lleva expedientes
 * como cualquiera —la constitucion le da todas las capacidades de un abogado mas
 * las suyas—, asi que filtrar por {@code role = 'LAWYER'} la dejaria fuera de una
 * lista cuyo proposito es repartir el trabajo del area. Se deja escrito porque es
 * el filtro que se anade sin pensar.
 *
 * <p>Una cuenta inactiva no puede recibir trabajo: el registro quedaria a nombre de
 * alguien que no puede entrar al sistema.
 */
@Repository
public class DestinosDeAsignacion {

    private final JdbcClient jdbc;

    public DestinosDeAsignacion(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Las cuentas activas, ordenadas por nombre.
     *
     * @param excepto responsable actual, que no tiene sentido ofrecer; null para no excluir
     */
    public List<Destino> activos(UUID excepto) {
        return jdbc.sql("""
                SELECT id, name, role
                FROM app_user
                WHERE status = 'ACTIVE'
                  AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid))
                ORDER BY lower(btrim(name)), id
                """)
                // Sin CAST, PostgreSQL no puede inferir el tipo de un parametro nulo.
                .param("excepto", excepto)
                .query(Destino.class)
                .list();
    }

    /** ¿Puede esta cuenta recibir trabajo ahora mismo? */
    public boolean puedeRecibir(UUID id) {
        Integer total = jdbc.sql(
                "SELECT count(*) FROM app_user WHERE id = :id AND status = 'ACTIVE'")
                .param("id", id).query(Integer.class).single();
        return total != null && total > 0;
    }

    /** Nombre de una cuenta, para los mensajes y el aviso previo al traspaso. */
    public String nombreDe(UUID id) {
        return jdbc.sql("SELECT name FROM app_user WHERE id = :id")
                .param("id", id).query(String.class).optional().orElse("(cuenta retirada)");
    }

    public record Destino(UUID id, String name, String role) {
        public boolean esJefa() {
            return "HEAD".equals(role);
        }
    }
}
