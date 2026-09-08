package pe.org.beneficencia.legalcontrol.config;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;
import pe.org.beneficencia.legalcontrol.calendar.FeriadosDelPeru;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository;

/**
 * Carga los feriados de ley del Peru para que la jefatura los revise.
 *
 * <pre>
 *   java -jar sistema-juridico.jar --spring.profiles.active=prod \
 *        --app.command=seed-holidays --app.years=2026,2027
 * </pre>
 *
 * <p><b>No confirma la cobertura de ningun ano, y no debe hacerlo.</b> El sistema
 * solo da por bueno un ano cuando existe una revision confirmada por una persona
 * ({@code calendar_review}), y esa condicion gobierna todo calculo de plazos. Si
 * este comando escribiera esa fila estaria afirmando «alguien comprobo estas
 * fechas» sobre una lista que salio de un programa. La jefa confirma en
 * {@code /dias-no-laborables} despues de mirarlas.
 *
 * <p>Por lo mismo, cada ano tocado sube su revision tecnica: si ya estaba
 * confirmado, la confirmacion deja de valer y hay que volver a revisarlo. Anadir
 * fechas a un ano ya revisado cambia el calendario que se reviso.
 *
 * <p><b>Solo carga anos vacios.</b> Si el ano ya tiene algun dia registrado se
 * deja entero como esta y el informe lo dice. Es la misma regla que
 * {@code seed-catalogs}, y por el mismo motivo: comprobar fecha por fecha parecia
 * mas util y es peligroso. Si la jefa retira el 15 de agosto porque su oficina no
 * lo observa, la siguiente ejecucion no lo encontraria y lo <b>repondria</b>,
 * deshaciendo su decision sin que nadie se entere.
 *
 * <p>El precio es que un ano al que ya se anadio un puente a mano no se puede
 * completar con el comando; hay que anadir el resto desde la pantalla. Se prefiere
 * ese estorbo a que una carga automatica revierta una decision del area.
 *
 * <p>Los puentes por decreto supremo no estan aqui: se declaran cada ano y no se
 * pueden calcular. Hay que anadirlos a mano.
 */
@Component
public class SeedHolidaysCommand implements ApplicationRunner {

    public static final String COMANDO = "seed-holidays";

    private final NonWorkingDayRepository dias;
    private final JdbcClient jdbc;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public SeedHolidaysCommand(NonWorkingDayRepository dias, JdbcClient jdbc,
                               AuditRecorder auditoria, Clock clock) {
        this.dias = dias;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        var valores = args.getOptionValues("app.command");
        if (valores == null || !valores.contains(COMANDO)) {
            return;
        }
        System.out.print(ejecutar(anosPedidos(args.getOptionValues("app.years"))));
    }

    /**
     * Los anos a cargar. Sin argumento: el anterior, el actual y el siguiente.
     *
     * <p>El anterior porque la antiguedad de un pendiente sin plazo se mide hacia
     * atras y cruza el cambio de ano; el siguiente porque en diciembre ya se fijan
     * plazos que caen en enero. Mas anos serian fechas que nadie necesita todavia y
     * que la jefa tendria que revisar igual.
     */
    private List<Integer> anosPedidos(List<String> argumento) {
        if (argumento == null || argumento.isEmpty()) {
            int ano = LocalDate.now(clock).getYear();
            return List.of(ano - 1, ano, ano + 1);
        }
        List<Integer> anos = new ArrayList<>();
        for (String parte : String.join(",", argumento).split(",")) {
            String limpio = parte.strip();
            if (!limpio.isEmpty()) {
                anos.add(Integer.parseInt(limpio));
            }
        }
        return List.copyOf(anos);
    }

    /** @return el informe de lo que se cargo, para mostrarlo o comprobarlo */
    @Transactional
    public String ejecutar(List<Integer> anos) {
        UUID jefa = primeraJefatura();
        if (jefa == null) {
            return """

                    No hay ninguna cuenta de jefatura activa. Cree la primera con
                    --app.command=bootstrap antes de cargar los feriados.
                    """;
        }

        StringBuilder informe = new StringBuilder("\n");
        informe.append("============================================================\n");
        informe.append(" Feriados de ley del Peru, cargados para su revision.\n");
        informe.append("============================================================\n\n");

        for (int ano : anos) {
            int yaHabia = dias.contarDelAno(ano);
            if (yaHabia > 0) {
                informe.append(String.format(" %d   ya tenia %d dias, se dejo intacto%n",
                        ano, yaHabia));
                continue;
            }

            int cargados = 0;
            for (var feriado : FeriadosDelPeru.delAno(ano, true)) {
                UUID id = dias.insertar(feriado.dia(), feriado.descripcion(), feriado.tipo(),
                        jefa, clock.instant());
                auditoria.registrar("NON_WORKING_DAY", id, "CREATE", jefa, jefa, null,
                        Map.of("day", feriado.dia().toString(),
                               "description", feriado.descripcion(),
                               "kind", feriado.tipo().name()),
                        "Feriado de ley cargado con --app.command=seed-holidays, pendiente"
                                + " de revision de la jefatura");
                cargados++;
            }

            // Subir la revision es lo que deja el ano pendiente de revisar. Sin esto,
            // una revision confirmada de cuando el ano estaba vacio pasaria a dar
            // por buenas unas fechas que nadie ha mirado.
            dias.tocarAno(ano, jefa, clock.instant());
            informe.append(String.format(" %d   %2d dias cargados, pendientes de revisar%n",
                    ano, cargados));
        }

        informe.append("""

                 REVISE LAS FECHAS ANTES DE CONFIRMAR LA COBERTURA.
                 Estan tomadas del Decreto Legislativo 713 con la ampliacion de la
                 Ley 31068, y el 15 de agosto de la Ley 24875 (provincia de
                 Arequipa). Jueves y Viernes Santo se calculan a partir de la Pascua.

                 FALTAN LOS PUENTES. Los dias no laborables que el Ejecutivo declara
                 cada ano por decreto supremo no son de ley y no se pueden calcular:
                 anadalos a mano en Dias no laborables.

                 Los anos que ya tenian dias no se tocaron. Este comando carga anos
                 vacios: nunca repone un dia que el area retiro.

                 Ningun ano queda confirmado por este comando. Entre en
                 /dias-no-laborables, revise la lista y confirme la cobertura.
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
}
