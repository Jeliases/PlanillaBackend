package pe.Planilla.service.excel;

import org.apache.poi.ss.usermodel.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/** Lectura tolerante de celdas de Excel (texto, fechas, horas) con mensajes de error en español. */
public final class ExcelLector {
    private ExcelLector() {}

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE);

    private static final List<DateTimeFormatter> FORMATOS_HORA = List.of(
            DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("H:mm:ss").withResolverStyle(ResolverStyle.STRICT));

    /** Minúsculas, sin tildes ni símbolos: «Número_Documento» y «numero documento» son la misma clave. */
    public static String clave(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static boolean filaVacia(Row fila, DataFormatter fmt) {
        if (fila == null) return true;
        for (Cell c : fila) {
            if (!fmt.formatCellValue(c).trim().isEmpty()) return false;
        }
        return true;
    }

    public static String texto(Cell c, DataFormatter fmt) {
        if (c == null) return null;
        String s = fmt.formatCellValue(c).trim();
        return s.isEmpty() ? null : s;
    }

    /** Documentos, teléfonos y cuentas: si Excel lo guardó como número, se lee sin decimales ni notación científica. */
    public static String textoNumerico(Cell c, DataFormatter fmt) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        String s = fmt.formatCellValue(c).trim();
        return s.isEmpty() ? null : s.replace(" ", "");
    }

    public static LocalDate fecha(Cell c, DataFormatter fmt, String nombreColumna) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) {
            return DateUtil.getJavaDate(c.getNumericCellValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = fmt.formatCellValue(c).trim();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignorada) {
                // se prueba el siguiente formato
            }
        }
        throw new RuntimeException("Fecha inválida «" + s + "» en " + nombreColumna + ". Use dd/mm/aaaa.");
    }

    /** Hora de una celda: puede venir como hora de Excel (fracción del día) o como texto «9:00» / «09:00». */
    public static LocalTime hora(Cell c, DataFormatter fmt, String nombreColumna) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) {
            double dia = c.getNumericCellValue() % 1;
            long segundos = Math.round(dia * 86400);
            return LocalTime.ofSecondOfDay(segundos % 86400).withSecond(0).withNano(0);
        }
        String s = fmt.formatCellValue(c).trim();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : FORMATOS_HORA) {
            try {
                return LocalTime.parse(s, f).withSecond(0).withNano(0);
            } catch (DateTimeParseException ignorada) {
                // se prueba el siguiente formato
            }
        }
        throw new RuntimeException("Hora inválida «" + s + "» en " + nombreColumna + ". Use hh:mm (por ejemplo 09:00).");
    }
}
