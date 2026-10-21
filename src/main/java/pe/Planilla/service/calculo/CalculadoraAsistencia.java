package pe.Planilla.service.calculo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.entity.HorarioProgramado;
import pe.Planilla.model.entity.ReglaTardanza;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de asistencia sin base de datos: ordenar las marcaciones del reloj, decidir si el día fue asistencia,
 * tardanza o falta, y calcular el descuento por tardanzas con la tabla del comunicado de la empresa.
 */
@Component
@RequiredArgsConstructor
public class CalculadoraAsistencia {

    /** Dos marcas con menos de 3 minutos de diferencia son un doble toque del reloj. */
    private static final long MINUTOS_DOBLE_TOQUE = 2;
    private static final LocalTime MEDIODIA = LocalTime.NOON;
    private static final LocalTime CORTE_ALMUERZO = LocalTime.of(14, 30);

    private final PlanillaProperties props;

    /** Las 4 marcaciones del día, en el orden del reloj: ingreso, almuerzo inicio, almuerzo fin, salida laboral. */
    public record Marcas(LocalTime ingreso, LocalTime almuerzoInicio, LocalTime almuerzoFin, LocalTime salida,
                         boolean incompleta, int cantidadLeidas) {
        public boolean hayMarcas() {
            return ingreso != null || salida != null || almuerzoInicio != null || almuerzoFin != null;
        }
    }

    public record ResultadoDia(String estado, int minutosTardanza, int minutosExtra, String observacion) {}

    public record DescuentoTardanzas(int cantidad, BigDecimal montoTramos, BigDecimal montoDiaAdicional, int fueraDeTabla) {
        public BigDecimal total() {
            return montoTramos.add(montoDiaAdicional);
        }
    }

    // ------------------------------------------------------------ marcaciones

    /**
     * Ordena las marcas leídas del reloj. Con 4 son ingreso, almuerzo inicio, almuerzo fin y salida. Si faltan o sobran,
     * el ingreso es la primera, la salida la última y se avisa con {@code incompleta = true} para revisarlo.
     */
    public Marcas distribuir(List<LocalTime> leidas) {
        List<LocalTime> m = new ArrayList<>();
        leidas.stream().sorted().forEach(t -> {
            if (m.isEmpty() || ChronoUnit.MINUTES.between(m.get(m.size() - 1), t) > MINUTOS_DOBLE_TOQUE) {
                m.add(t);
            }
        });
        int n = m.size();
        return switch (n) {
            case 0 -> new Marcas(null, null, null, null, false, 0);
            case 1 -> m.get(0).isBefore(MEDIODIA)
                    ? new Marcas(m.get(0), null, null, null, true, 1)
                    : new Marcas(null, null, null, m.get(0), true, 1);
            case 2 -> new Marcas(m.get(0), null, null, m.get(1), false, 2);
            case 3 -> m.get(1).isBefore(CORTE_ALMUERZO)
                    ? new Marcas(m.get(0), m.get(1), null, m.get(2), true, 3)
                    : new Marcas(m.get(0), null, m.get(1), m.get(2), true, 3);
            case 4 -> new Marcas(m.get(0), m.get(1), m.get(2), m.get(3), false, 4);
            default -> new Marcas(m.get(0), m.get(1), m.get(n - 2), m.get(n - 1), true, n);
        };
    }

    // ------------------------------------------------------------------ el día

    /** ¿Se espera que el trabajador venga ese día? Feriado = no. Con horario programado manda el horario; sin él, lunes a viernes. */
    public boolean esLaborable(LocalDate fecha, HorarioProgramado programado, boolean esFeriado) {
        if (esFeriado) return false;
        if (programado != null) return !programado.isDescanso();
        DayOfWeek d = fecha.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    public LocalTime entradaProgramada(HorarioProgramado h) {
        return (h != null && h.getHoraEntrada() != null) ? h.getHoraEntrada() : props.getHoraEntradaDefecto();
    }

    public LocalTime salidaProgramada(HorarioProgramado h) {
        return (h != null && h.getHoraSalida() != null) ? h.getHoraSalida() : props.getHoraSalidaDefecto();
    }

    /**
     * Resultado del día. Devuelve null si no hay nada que registrar (día de descanso sin marcas).
     * Tardanza = minutos de atraso sobre la entrada programada (9:01 es 1 minuto). Extra = minutos después de la salida programada.
     */
    public ResultadoDia evaluar(Marcas marcas, LocalTime entradaProg, LocalTime salidaProg, boolean laborable) {
        boolean hay = marcas.hayMarcas();
        if (!laborable) {
            return hay ? new ResultadoDia("ASISTIO", 0, 0, "Trabajó un día de descanso o feriado") : null;
        }
        if (!hay) {
            return new ResultadoDia("FALTA", 0, 0, "Sin marcaciones en un día laborable");
        }
        List<String> notas = new ArrayList<>();
        int tardanza = 0;
        if (marcas.ingreso() != null) {
            tardanza = (int) Math.max(0, ChronoUnit.MINUTES.between(entradaProg, marcas.ingreso()));
        } else {
            notas.add("Sin marca de ingreso");
        }
        int extra = 0;
        if (marcas.salida() != null) {
            extra = (int) Math.max(0, ChronoUnit.MINUTES.between(salidaProg, marcas.salida()));
        } else {
            notas.add("Sin marca de salida");
        }
        if (marcas.incompleta() && marcas.cantidadLeidas() > 4) {
            notas.add("Marcaciones de más (" + marcas.cantidadLeidas() + ")");
        } else if (marcas.incompleta() && marcas.cantidadLeidas() == 3) {
            notas.add("Falta una marca de almuerzo");
        }
        return new ResultadoDia(tardanza > 0 ? "TARDANZA" : "ASISTIO", tardanza, extra,
                notas.isEmpty() ? null : String.join("; ", notas));
    }

    // -------------------------------------------------------------- tardanzas

    /**
     * Descuento del mes por tardanzas injustificadas. Cada tardanza cae en un tramo de la tabla (por minutos de atraso):
     * un monto fijo, medio día o un día de sueldo. Con {@code tardanzasParaDiaAdicional} tardanzas en el mes se
     * descuenta además un día. Si el atraso supera el último tramo se aplica ese último tramo y se cuenta aparte.
     */
    public DescuentoTardanzas descuentoTardanzas(List<Integer> minutosPorTardanza, BigDecimal sueldoBase, List<ReglaTardanza> reglas) {
        List<ReglaTardanza> tabla = reglas.stream()
                .sorted((a, b) -> Integer.compare(a.getDesdeMinuto(), b.getDesdeMinuto())).toList();
        BigDecimal total = BigDecimal.ZERO;
        int cantidad = 0;
        int fuera = 0;
        for (Integer min : minutosPorTardanza) {
            if (min == null || min <= 0 || tabla.isEmpty()) continue;
            cantidad++;
            ReglaTardanza regla = tabla.stream()
                    .filter(r -> min >= r.getDesdeMinuto() && (r.getHastaMinuto() == null || min <= r.getHastaMinuto()))
                    .findFirst().orElse(null);
            if (regla == null) {
                regla = tabla.get(tabla.size() - 1);
                fuera++;
            }
            total = total.add(montoDe(regla, sueldoBase));
        }
        BigDecimal diaAdicional = cantidad >= props.getTardanzasParaDiaAdicional() && props.getTardanzasParaDiaAdicional() > 0
                ? sueldoBase.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new DescuentoTardanzas(cantidad, total.setScale(2, RoundingMode.HALF_UP), diaAdicional, fuera);
    }

    private BigDecimal montoDe(ReglaTardanza regla, BigDecimal sueldoBase) {
        return switch (regla.getTipo()) {
            case ReglaTardanza.MEDIO_DIA -> sueldoBase.divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
            case ReglaTardanza.DIA -> sueldoBase.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
            default -> regla.getMonto() != null ? regla.getMonto() : BigDecimal.ZERO;
        };
    }
}
