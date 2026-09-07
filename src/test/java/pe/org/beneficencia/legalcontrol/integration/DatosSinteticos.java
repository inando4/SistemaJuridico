package pe.org.beneficencia.legalcontrol.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Genera el volumen del presupuesto de rendimiento: 5.000 expedientes y 50 estados.
 *
 * <p><b>Todo inventado.</b> Ningun nombre, numero de expediente o monto procede de
 * un caso real: son datos de una institucion publica y no tienen por que estar en
 * un entorno de pruebas. Los feriados tampoco son los oficiales, para que nadie
 * confunda estas filas con el calendario de verdad.
 */
public final class DatosSinteticos {

    private static final List<String> MATERIAS = List.of(
            "Desalojo", "Cobro de soles", "Obligacion de dar suma de dinero",
            "Reivindicacion", "Nulidad de acto juridico", "Interdicto de recobrar",
            "Prescripcion adquisitiva", "Otorgamiento de escritura publica");

    private DatosSinteticos() {
    }

    /** @return los identificadores de los estados creados */
    public static List<UUID> sembrar(JdbcClient jdbc, UUID responsable,
                                     int expedientes, int estados) {
        Random azar = new Random(20260907L);   // semilla fija: volumen reproducible
        Timestamp ahora = Timestamp.from(Instant.now());

        List<UUID> catalogo = new java.util.ArrayList<>();
        for (int i = 1; i <= estados; i++) {
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO procedural_status (id, name, enabled, created_by,
                                                   created_at, updated_at, version)
                    VALUES (:id, :nombre, true, :actor, :ahora, :ahora, 1)
                    """).param("id", id).param("nombre", "Estado sintetico " + i)
                    .param("actor", responsable).param("ahora", ahora).update();
            catalogo.add(id);
        }

        for (int i = 1; i <= expedientes; i++) {
            LocalDate limite = azar.nextInt(10) == 0
                    ? null
                    : LocalDate.now().plusDays(azar.nextInt(400) - 100);

            jdbc.sql("""
                    INSERT INTO judicial_case
                        (id, sequence_number, owner_id, case_number, claimant, respondent,
                         subject, procedural_status_id, deadline, amount, active,
                         created_at, updated_at, version)
                    VALUES (:id, :seq, :owner, :numero, :dte, :ddo, :materia, :estado,
                            :limite, :monto, :activo, :ahora, :ahora, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("seq", i)
                    .param("owner", responsable)
                    .param("numero", String.format("%05d-2026-0-0401-JR-CI-%02d", i, azar.nextInt(20) + 1))
                    .param("dte", "Demandante sintetico " + i)
                    .param("ddo", "Demandado sintetico " + i)
                    .param("materia", MATERIAS.get(azar.nextInt(MATERIAS.size())))
                    .param("estado", catalogo.get(azar.nextInt(catalogo.size())))
                    .param("limite", limite)
                    .param("monto", java.math.BigDecimal.valueOf(azar.nextInt(500_000)))
                    .param("activo", azar.nextInt(10) != 0)
                    .param("ahora", ahora)
                    .update();
        }
        return catalogo;
    }

    private static final List<String> AREAS = List.of(
            "Gerencia General", "Administracion", "Contabilidad", "Recursos Humanos",
            "Logistica", "Servicios Sociales", "Patrimonio");

    /**
     * Siembra procedimientos administrativos y su catalogo propio.
     *
     * <p>Datos inventados, igual que los judiciales: ningun area, numero ni pedido
     * procede de un caso real.
     *
     * @return los identificadores de los estados administrativos creados
     */
    public static List<UUID> sembrarAdministrativos(JdbcClient jdbc, UUID responsable,
                                                    int procedimientos, int estados) {
        Random azar = new Random(20260907L);
        Timestamp ahora = Timestamp.from(Instant.now());

        List<UUID> catalogo = new java.util.ArrayList<>();
        for (int i = 1; i <= estados; i++) {
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO administrative_status (id, name, enabled, created_by,
                                                       created_at, updated_at, version)
                    VALUES (:id, :nombre, true, :actor, :ahora, :ahora, 1)
                    """).param("id", id).param("nombre", "Estado administrativo sintetico " + i)
                    .param("actor", responsable).param("ahora", ahora).update();
            catalogo.add(id);
        }

        for (int i = 1; i <= procedimientos; i++) {
            LocalDate recepcion = LocalDate.now().minusDays(azar.nextInt(120));
            LocalDate limite = azar.nextInt(10) == 0 ? null : recepcion.plusDays(15 + azar.nextInt(200));

            jdbc.sql("""
                    INSERT INTO administrative_procedure
                        (id, sequence_number, owner_id, file_number, requesting_area, request,
                         administrative_status_id, received_at, deadline, notes, active,
                         created_at, updated_at, version)
                    VALUES (:id, :seq, :owner, :numero, :area, :pedido, :estado,
                            :recepcion, :limite, :notas, :activo, :ahora, :ahora, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("seq", i)
                    .param("owner", responsable)
                    .param("numero", String.format("ADM-%05d-2026", i))
                    .param("area", AREAS.get(azar.nextInt(AREAS.size())))
                    .param("pedido", "Pedido sintetico de prueba numero " + i)
                    .param("estado", catalogo.get(azar.nextInt(catalogo.size())))
                    .param("recepcion", recepcion)
                    .param("limite", limite)
                    .param("notas", "Observacion sintetica " + i)
                    .param("activo", azar.nextInt(10) != 0)
                    .param("ahora", ahora)
                    .update();
        }
        return catalogo;
    }

    /** Calendario sintetico revisado, para que los plazos den numeros y no avisos. */
    public static void sembrarCalendario(JdbcClient jdbc, UUID responsable, int... anos) {
        Timestamp ahora = Timestamp.from(Instant.now());
        for (int ano : anos) {
            jdbc.sql("""
                    INSERT INTO calendar_year (year, revision, created_by, created_at)
                    VALUES (:ano, 1, :actor, :ahora) ON CONFLICT (year) DO NOTHING
                    """).param("ano", ano).param("actor", responsable).param("ahora", ahora).update();

            for (int mes = 1; mes <= 12; mes++) {
                jdbc.sql("""
                        INSERT INTO non_working_day (id, day, description, kind, created_by,
                                                     created_at, updated_at, version)
                        VALUES (:id, :dia, 'Dia sintetico de prueba', 'OTHER', :actor,
                                :ahora, :ahora, 1)
                        ON CONFLICT (day) DO NOTHING
                        """).param("id", UUID.randomUUID())
                        .param("dia", LocalDate.of(ano, mes, 15))
                        .param("actor", responsable).param("ahora", ahora).update();
            }

            jdbc.sql("""
                    INSERT INTO calendar_review (id, year, reviewed_revision, reviewed_by,
                                                 reviewed_at, full_year_reviewed)
                    VALUES (:id, :ano, 1, :actor, :ahora, true)
                    ON CONFLICT (year, reviewed_revision) DO NOTHING
                    """).param("id", UUID.randomUUID()).param("ano", ano)
                    .param("actor", responsable).param("ahora", ahora).update();
        }
    }
}
