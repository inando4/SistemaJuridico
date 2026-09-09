package pe.org.beneficencia.legalcontrol.pendingtask;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * El expediente al que se quiere vincular un pendiente, resuelto de una vez.
 *
 * <p>Tres preguntas se hacen en el mismo instante y las tres las responde la misma
 * fila:
 *
 * <ol>
 *   <li><b>¿Existe?</b> Si no hay fila, el vinculo se rechaza. Sin esta pregunta, un
 *       identificador inventado llegaba hasta la clave foranea y salia como error
 *       500. Desde el desplegable era inalcanzable; con un parametro en la URL esta
 *       a un clic.
 *   <li><b>¿Como se llama?</b> Para la opcion del desplegable y para el aviso del
 *       listado filtrado. Un UUID en pantalla no le dice nada a nadie.
 *   <li><b>¿De quien es?</b> Para avisar, antes de guardar, cuando el expediente es
 *       de otra persona y el pendiente va a quedar a nombre de quien lo registra.
 * </ol>
 *
 * <p>Resolverlas por separado costaria tres consultas en una pantalla que debe
 * costar una.
 */
public record ExpedienteVinculado(
        UUID id,
        String numero,
        UUID responsableId,
        String responsableNombre,
        boolean activo,
        Clase clase) {

    /**
     * Judicial o administrativo.
     *
     * <p>Hace falta saberlo: al añadir la opcion que le falta a un desplegable, un
     * expediente judicial no puede acabar en la lista de procedimientos
     * administrativos. Ofreceria un vinculo que la validacion rechazaria despues, y
     * el usuario no entenderia por que.
     */
    public enum Clase { JUDICIAL, ADMINISTRATIVO }

    /** ¿Lo lleva otra persona distinta de quien esta registrando? */
    public boolean deOtraPersona(UUID quienRegistra) {
        return quienRegistra != null && !quienRegistra.equals(responsableId);
    }

    @Component
    public static class Buscador {

        private final JdbcClient jdbc;

        public Buscador(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        /**
         * Un expediente judicial, exista o no.
         *
         * <p>Sin filtro por {@code active}: la ficha de un expediente archivado se
         * abre con normalidad —{@code porId} tampoco filtra—, asi que su enlace de
         * alta tiene que poder resolverse. Que este archivado se devuelve como dato,
         * para que el desplegable sepa que tiene que añadir la opcion.
         */
        public Optional<ExpedienteVinculado> judicial(UUID id) {
            if (id == null) {
                return Optional.empty();
            }
            return jdbc.sql("""
                    SELECT c.id, c.case_number AS numero,
                           c.owner_id AS responsable_id, u.name AS responsable_nombre,
                           c.active AS activo, 'JUDICIAL' AS clase
                    FROM judicial_case c
                    JOIN app_user u ON u.id = c.owner_id
                    WHERE c.id = :id
                    """).param("id", id).query(ExpedienteVinculado.class).optional();
        }

        /** Lo mismo para un procedimiento administrativo. */
        public Optional<ExpedienteVinculado> administrativo(UUID id) {
            if (id == null) {
                return Optional.empty();
            }
            return jdbc.sql("""
                    SELECT p.id, p.file_number AS numero,
                           p.owner_id AS responsable_id, u.name AS responsable_nombre,
                           p.active AS activo, 'ADMINISTRATIVO' AS clase
                    FROM administrative_procedure p
                    JOIN app_user u ON u.id = p.owner_id
                    WHERE p.id = :id
                    """).param("id", id).query(ExpedienteVinculado.class).optional();
        }

        /** ¿Existe el expediente judicial indicado? */
        public boolean existeJudicial(UUID id) {
            return judicial(id).isPresent();
        }

        /** ¿Existe el procedimiento administrativo indicado? */
        public boolean existeAdministrativo(UUID id) {
            return administrativo(id).isPresent();
        }
    }
}
