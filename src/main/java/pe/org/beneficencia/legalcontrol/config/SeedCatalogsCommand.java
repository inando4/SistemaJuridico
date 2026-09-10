package pe.org.beneficencia.legalcontrol.config;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga los valores de catalogo que el cliente enumera en el insumo.
 *
 * <pre>
 *   java -jar sistema-juridico.jar --spring.profiles.active=prod --app.command=seed-catalogs
 * </pre>
 *
 * <p><b>Por que un comando y no una siembra en la migracion.</b> Los catalogos son
 * administrables a proposito: el area puede renombrar, anadir o retirar valores sin
 * depender del programador. Sembrarlos en una migracion los convertiria en parte del
 * esquema, y cambiar uno exigiria una migracion nueva.
 *
 * <p>Ademas, el catalogo vacio debe seguir siendo un estado valido y comprobado: es
 * lo que garantiza que el sistema no se rompa si alguien retira un valor.
 *
 * <p><b>Solo carga catalogos vacios.</b> Si uno ya tiene valores, se deja intacto y
 * el informe lo dice. Es una carga inicial, no una sincronizacion.
 *
 * <p>La alternativa —comprobar valor por valor si existe— parecia mas util y es
 * peligrosa: si el area renombra «En tramite» a «En tramite judicial», la siguiente
 * ejecucion no encontraria el original y lo <b>repondria</b>, dejando dos valores
 * donde el area queria uno. Un comando de carga no debe poder deshacer una decision
 * del area.
 *
 * <p>Los valores quedan atribuidos a la primera cuenta de jefatura, y auditados como
 * cualquier alta hecha desde la aplicacion.
 */
@Component
public class SeedCatalogsCommand implements ApplicationRunner {

    public static final String COMANDO = "seed-catalogs";

    /** Lo que el insumo enumera, por catalogo. Secciones 6.1, 7.1, 10, 11 y 12. */
    private static final Map<String, List<String>> VALORES = Map.of(
            "procedural_status", List.of(
                    "Pendiente de actuación", "En trámite", "Concluido", "Archivado", "Ejecución"),
            "administrative_status", List.of(
                    "Pendiente de atención", "Pendiente de documentación", "Atendido",
                    "Observado", "Archivado"),
            "pending_task_type", List.of(
                    "Informe legal", "Informe", "Oficio", "Carta", "Convenio", "Revisión",
                    "Solicitud de información", "Escrito judicial", "Audiencia", "Alegatos",
                    "Seguimiento", "Reunión", "Otro"),
            "priority", List.of("Alta", "Media", "Baja"),
            // Solo los tres estados de TRABAJO. La seccion 12 enumera ademas
            // «Reprogramado», «Cumplido» y «Cancelado», que en este sistema no son
            // etiquetas sino acciones: cumplido es la fecha de cumplimiento que pone
            // el boton, cancelado es la marca de visibilidad, y reprogramado es lo que
            // hace «No cumplido» al mover la fecha.
            //
            // Cargarlas como opciones del desplegable pondria «Cumplido» al lado de un
            // pendiente que no lo esta. Elegirlo es lo mas natural del mundo, y no
            // haria nada: el pendiente seguiria en la lista de trabajo, contando como
            // activo y fuera de /cumplidos. El usuario concluiria que el sistema falla.
            //
            // No contradice al cliente: la propia seccion 12 cierra diciendo que el
            // equipo puede renombrar los estados o anadir otros. Empezar con tres y
            // crecer es mas facil que empezar con seis y averiguar cual miente.
            // Anotado en PENDIENTE-CLIENTE.md para comentarlo con la jefatura.
            "pending_task_status", List.of(
                    "Pendiente", "En proceso", "Pendiente de información"));

    /** Tipo de entidad de auditoria de cada catalogo. */
    private static final Map<String, String> ENTIDADES = Map.of(
            "procedural_status", "PROCEDURAL_STATUS",
            "administrative_status", "ADMINISTRATIVE_STATUS",
            "pending_task_type", "PENDING_TASK_TYPE",
            "priority", "PRIORITY",
            "pending_task_status", "PENDING_TASK_STATUS");

    private final JdbcClient jdbc;
    private final Clock clock;

    public SeedCatalogsCommand(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        var valores = args.getOptionValues("app.command");
        if (valores == null || !valores.contains(COMANDO)) {
            return;
        }
        System.out.print(ejecutar());
    }

    /** @return el informe de lo que se cargo, para mostrarlo o comprobarlo */
    @Transactional
    public String ejecutar() {
        UUID jefa = primeraJefatura();
        if (jefa == null) {
            return """

                    No hay ninguna cuenta de jefatura activa. Cree la primera con
                    --app.command=bootstrap antes de cargar los catalogos.
                    """;
        }

        StringBuilder informe = new StringBuilder("\n");
        informe.append("============================================================\n");
        informe.append(" Catalogos cargados desde el insumo del cliente.\n");
        informe.append("============================================================\n\n");

        // Orden fijo: un Map no lo garantiza y el informe debe ser reproducible.
        for (String tabla : List.of("procedural_status", "administrative_status",
                "pending_task_type", "priority", "pending_task_status")) {
            if (!estaVacio(tabla)) {
                informe.append(String.format(" %-24s ya tenia valores, se dejo intacto%n", tabla));
                continue;
            }
            for (String nombre : VALORES.get(tabla)) {
                insertar(tabla, nombre, jefa);
            }
            informe.append(String.format(" %-24s %2d valores cargados%n",
                    tabla, VALORES.get(tabla).size()));
        }

        informe.append("""

                 Los catalogos que ya tenian valores no se tocaron. Este comando
                 carga, no sincroniza: nunca deshace una decision del area.

                 El catalogo sigue siendo administrable desde la aplicacion.
                ============================================================
                """);
        return informe.toString();
    }

    private UUID primeraJefatura() {
        return jdbc.sql("""
                SELECT id FROM app_user
                WHERE role = 'HEAD' AND status = 'ACTIVE'
                ORDER BY created_at LIMIT 1
                """).query(UUID.class).optional().orElse(null);
    }

    private boolean estaVacio(String tabla) {
        // El nombre de tabla es constante del propio codigo, nunca entrada externa.
        Integer total = jdbc.sql("SELECT count(*) FROM " + tabla)
                .query(Integer.class).single();
        return total == null || total == 0;
    }

    private void insertar(String tabla, String nombre, UUID jefa) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(clock.instant());

        jdbc.sql("INSERT INTO " + tabla + " (id, name, enabled, created_by,"
                        + " created_at, updated_at, version)"
                        + " VALUES (:id, :nombre, true, :jefa, :ahora, :ahora, 1)")
                .param("id", id).param("nombre", nombre)
                .param("jefa", jefa).param("ahora", ahora).update();

        // Se audita como cualquier alta hecha desde la aplicacion: quien pregunte
        // de donde salio un valor tiene que poder averiguarlo.
        jdbc.sql("""
                INSERT INTO audit_event (id, entity_type, entity_id, action, actor_id,
                                         owner_id, occurred_at, after_values, reason)
                VALUES (:evento, :entidad, :id, 'CREATE', :jefa, :jefa, :ahora,
                        CAST(:despues AS jsonb), 'Carga inicial desde el insumo del cliente')
                """)
                .param("evento", UUID.randomUUID()).param("entidad", ENTIDADES.get(tabla))
                .param("id", id).param("jefa", jefa).param("ahora", ahora)
                .param("despues", "{\"name\":\"" + nombre.replace("\"", "\\\"")
                        + "\",\"enabled\":true}")
                .update();
    }
}
