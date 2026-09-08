package pe.org.beneficencia.legalcontrol.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;

/**
 * Los feriados de ley del Peru, calculados para cualquier ano.
 *
 * <p><b>Se calculan, no se copian.</b> Una lista escrita a mano para 2026 queda
 * inservible en enero de 2027, y este es un dato que el area necesita cargar cada
 * ano. Las catorce fechas fijas vienen de ley; Jueves y Viernes Santo se derivan
 * del domingo de Pascua, que se mueve.
 *
 * <p><b>Base legal.</b> Los feriados nacionales son los del Decreto Legislativo 713,
 * ampliados por la Ley 31068 (2020), que anadio el 7 de junio, el 23 de julio, el 6
 * de agosto y el 9 de diciembre. El 15 de agosto es dia civico no laborable en la
 * <b>provincia de Arequipa</b> por Ley 24875, y por eso se marca como regional y no
 * como nacional: la distincion importa si algun dia el area trabaja fuera.
 *
 * <p><b>Lo que esta lista NO contiene.</b> Los «dias no laborables» que el Ejecutivo
 * declara cada ano por decreto supremo —los puentes— no son de ley: cambian de un
 * ano a otro y se publican con pocos meses de antelacion. No se pueden calcular y
 * hay que anadirlos a mano desde la pantalla del calendario.
 */
public final class FeriadosDelPeru {

    /** Un dia de la lista, con la descripcion que vera la jefa al revisarlo. */
    public record Feriado(LocalDate dia, String descripcion, Tipo tipo) {}

    /** Las catorce fechas de ley que caen siempre en el mismo dia del ano. */
    private static final List<int[]> FIJOS = List.of(
            new int[] {1, 1}, new int[] {5, 1}, new int[] {6, 7}, new int[] {6, 29},
            new int[] {7, 23}, new int[] {7, 28}, new int[] {7, 29}, new int[] {8, 6},
            new int[] {8, 30}, new int[] {10, 8}, new int[] {11, 1}, new int[] {12, 8},
            new int[] {12, 9}, new int[] {12, 25});

    private static final List<String> NOMBRES_FIJOS = List.of(
            "Año Nuevo",
            "Día del Trabajo",
            "Batalla de Arica y Día de la Bandera",
            "San Pedro y San Pablo",
            "Día de la Fuerza Aérea del Perú",
            "Fiestas Patrias",
            "Fiestas Patrias",
            "Batalla de Junín",
            "Santa Rosa de Lima",
            "Combate de Angamos",
            "Todos los Santos",
            "Inmaculada Concepción",
            "Batalla de Ayacucho",
            "Navidad",
            "Aniversario de Arequipa (Ley 24875)");

    private FeriadosDelPeru() {}

    /**
     * Los feriados del ano, en orden de fecha.
     *
     * @param incluirArequipa si anadir el 15 de agosto, que solo rige en la provincia
     */
    public static List<Feriado> delAno(int ano, boolean incluirArequipa) {
        List<Feriado> feriados = new ArrayList<>();

        for (int i = 0; i < FIJOS.size(); i++) {
            int[] fecha = FIJOS.get(i);
            feriados.add(new Feriado(LocalDate.of(ano, fecha[0], fecha[1]),
                    NOMBRES_FIJOS.get(i), Tipo.NATIONAL_HOLIDAY));
        }

        LocalDate pascua = domingoDePascua(ano);
        feriados.add(new Feriado(pascua.minusDays(3), "Jueves Santo", Tipo.NATIONAL_HOLIDAY));
        feriados.add(new Feriado(pascua.minusDays(2), "Viernes Santo", Tipo.NATIONAL_HOLIDAY));

        if (incluirArequipa) {
            feriados.add(new Feriado(LocalDate.of(ano, 8, 15),
                    NOMBRES_FIJOS.get(NOMBRES_FIJOS.size() - 1), Tipo.REGIONAL_HOLIDAY));
        }

        feriados.sort(java.util.Comparator.comparing(Feriado::dia));
        return List.copyOf(feriados);
    }

    /**
     * El domingo de Pascua del ano, por el algoritmo de Meeus/Jones/Butcher.
     *
     * <p>Es aritmetica del calendario gregoriano, no una aproximacion: vale para
     * cualquier ano desde 1583. Las divisiones son enteras a proposito.
     */
    public static LocalDate domingoDePascua(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
