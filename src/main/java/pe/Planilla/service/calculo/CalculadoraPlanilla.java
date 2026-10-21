package pe.Planilla.service.calculo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.entity.Afp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de cálculo de la planilla, sin acceso a base de datos (así se prueban con números y no con datos).
 * Convención: mes comercial de 30 días; todos los montos se redondean a 2 decimales HALF_UP.
 */
@Component
@RequiredArgsConstructor
public class CalculadoraPlanilla {

    private static final BigDecimal DIAS_MES = new BigDecimal("30");
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final PlanillaProperties props;

    public record LineaPension(String codigo, String descripcion, BigDecimal monto) {}

    public record Gratificacion(int mesesComputables, BigDecimal bruta, BigDecimal descuentoFaltas,
                                BigDecimal neta, BigDecimal bonificacionExtraordinaria) {
        public static Gratificacion vacia() {
            BigDecimal cero = BigDecimal.ZERO.setScale(2);
            return new Gratificacion(0, cero, cero, cero, cero);
        }
    }

    public record Cts(int meses, int dias, BigDecimal monto) {}

    // ---------------------------------------------------------------- Días

    /** Días del mes que corresponde pagar: 30 si ya estaba contratado; proporcional si ingresó durante el mes; 0 si ingresó después. */
    public int diasDelPeriodo(LocalDate fechaIngreso, YearMonth periodo) {
        return diasDelPeriodo(fechaIngreso, null, periodo);
    }

    /**
     * Igual, considerando también el cese: si cesó dentro del mes se pagan los días hasta el cese (mes comercial de 30).
     * Ejemplo: ingresó antes y cesó el 15 -> 15 días; ingresó el 10 y cesó el 20 -> 11 días.
     */
    public int diasDelPeriodo(LocalDate fechaIngreso, LocalDate fechaCese, YearMonth periodo) {
        LocalDate inicioMes = periodo.atDay(1);
        LocalDate finMes = periodo.atEndOfMonth();
        if (fechaIngreso != null && fechaIngreso.isAfter(finMes)) return 0;
        if (fechaCese != null && fechaCese.isBefore(inicioMes)) return 0;
        int diaDesde = (fechaIngreso == null || !fechaIngreso.isAfter(inicioMes)) ? 1 : fechaIngreso.getDayOfMonth();
        int diaHasta = (fechaCese == null || !fechaCese.isBefore(finMes)) ? 30 : Math.min(30, fechaCese.getDayOfMonth());
        if (diaDesde == 1 && diaHasta == 30) return 30;
        // Si ingresó, se cuentan los días reales desde el ingreso hasta fin de mes (31 cuenta como 30)
        if (diaHasta == 30) return Math.min(30, periodo.lengthOfMonth() - diaDesde + 1);
        return Math.max(0, diaHasta - diaDesde + 1);
    }

    /** Días a pagar como sueldo: se restan las faltas injustificadas y los días de vacaciones (que se pagan aparte). */
    public int diasPagados(int diasPeriodo, long faltas, int diasVacaciones) {
        return (int) Math.max(0, diasPeriodo - faltas - diasVacaciones);
    }

    /** mensual * dias / 30. */
    public BigDecimal prorratear(BigDecimal mensual, int dias) {
        return mensual.multiply(new BigDecimal(dias)).divide(DIAS_MES, 2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------- Jornada

    /** Básico del mes según la jornada: el sueldo es a jornada completa (8 h); con menos horas se paga sueldo / 8 x horas. */
    public BigDecimal basicoMensual(BigDecimal sueldoJornadaCompleta, Integer horasJornada) {
        if (horasJornada == null || horasJornada >= 8) return sueldoJornadaCompleta;
        return sueldoJornadaCompleta.multiply(new BigDecimal(horasJornada)).divide(new BigDecimal("8"), 2, RoundingMode.HALF_UP);
    }

    /** Valor de una hora normal: básico del mes / 30 días / horas de la jornada (4 decimales para no perder precisión). */
    public BigDecimal valorHora(BigDecimal basicoMensual, Integer horasJornada) {
        int horas = horasJornada == null || horasJornada < 1 ? 8 : horasJornada;
        return basicoMensual.divide(DIAS_MES, 6, RoundingMode.HALF_UP).divide(new BigDecimal(horas), 4, RoundingMode.HALF_UP);
    }

    /** Un trabajador a tiempo parcial (menos de 4 horas al día) no tiene derecho a CTS. */
    public boolean tieneCts(Integer horasJornada) {
        return horasJornada == null || horasJornada >= 4;
    }

    /** Pago por trabajar en feriado o día de descanso: (básico + asignación familiar) / 30 por cada día (la planilla de la contadora). */
    public BigDecimal feriadoLaborado(BigDecimal basicoMasAsignacion, int dias) {
        return prorratear(basicoMasAsignacion, dias);
    }

    /** Promedio mensual de los ingresos variables de un semestre: suma / 6 (como la planilla de la contadora). */
    public BigDecimal promedioSemestral(BigDecimal sumaSemestre) {
        if (sumaSemestre == null || sumaSemestre.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return sumaSemestre.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------- Asignación familiar

    /** 10% de la RMV. */
    public BigDecimal asignacionFamiliarMensual() {
        return props.getRmv().multiply(props.getAsignacionFamiliarPct()).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------ Pensiones

    /** Igual que {@link #pension(String, Afp, String, BigDecimal)} con comisión por flujo. */
    public List<LineaPension> pension(String sistema, Afp afp, BigDecimal baseAsegurable) {
        return pension(sistema, afp, "FLUJO", baseAsegurable);
    }

    /**
     * ONP (13%) o AFP (aporte obligatorio + prima con tope + comisión). Se calcula sobre la remuneración asegurable.
     * Con comisión MIXTA no se descuenta comisión: el componente de flujo es 0% y el de saldo lo cobra el fondo.
     */
    public List<LineaPension> pension(String sistema, Afp afp, String tipoComision, BigDecimal baseAsegurable) {
        if ("NINGUNO".equalsIgnoreCase(sistema)) {
            return List.of(); // jubilado / no aportante: sin descuento de pensión
        }
        if ("AFP".equals(sistema)) {
            if (afp == null) {
                throw new RuntimeException("El empleado está en AFP pero no tiene un fondo asignado.");
            }
            BigDecimal tope = afp.getRemuneracionMaximaAsegurable();
            BigDecimal basePrima = tope != null ? baseAsegurable.min(tope) : baseAsegurable;
            List<LineaPension> lineas = new ArrayList<>();
            lineas.add(new LineaPension(CodigosPlame.AFP_APORTE,
                    "AFP " + afp.getNombre() + " - Aporte obligatorio (" + pct(afp.getAporteObligatorio()) + ")",
                    aplicar(baseAsegurable, afp.getAporteObligatorio())));
            if (!"MIXTA".equalsIgnoreCase(tipoComision)) {
                lineas.add(new LineaPension(CodigosPlame.AFP_COMISION,
                        "AFP " + afp.getNombre() + " - Comisión (" + pct(afp.getComisionFlujo()) + ")",
                        aplicar(baseAsegurable, afp.getComisionFlujo())));
            }
            lineas.add(new LineaPension(CodigosPlame.AFP_PRIMA,
                    "AFP " + afp.getNombre() + " - Prima de seguro (" + pct(afp.getPrimaSeguro()) + ")",
                    aplicar(basePrima, afp.getPrimaSeguro())));
            return lineas;
        }
        return List.of(new LineaPension(CodigosPlame.ONP, "ONP (" + pct(props.getOnpPct()) + ")",
                aplicar(baseAsegurable, props.getOnpPct())));
    }

    /** Aporte del empleador a EsSalud: 9% con piso en la RMV. */
    public BigDecimal essaludEmpleador(BigDecimal baseAfecta) {
        return aplicar(baseAfecta.max(props.getRmv()), props.getEssaludPct());
    }

    // -------------------------------------------------------- Gratificación

    /**
     * Meses calendario completos trabajados en el semestre de la gratificación (enero-junio para julio,
     * julio-diciembre para diciembre). Una fracción de mes no cuenta.
     */
    public int mesesComputablesGratificacion(LocalDate fechaIngreso, YearMonth periodo) {
        int mesIni = periodo.getMonthValue() == 7 ? 1 : 7;
        int mesFin = mesIni + 5;
        LocalDate inicioSemestre = YearMonth.of(periodo.getYear(), mesIni).atDay(1);
        if (fechaIngreso == null || !fechaIngreso.isAfter(inicioSemestre)) return 6;
        if (fechaIngreso.getYear() > periodo.getYear()) return 0;
        int primerMesCompleto = fechaIngreso.getDayOfMonth() == 1 ? fechaIngreso.getMonthValue() : fechaIngreso.getMonthValue() + 1;
        return Math.max(0, Math.min(6, mesFin - primerMesCompleto + 1));
    }

    public LocalDate inicioSemestreGratificacion(YearMonth periodo) {
        return YearMonth.of(periodo.getYear(), periodo.getMonthValue() == 7 ? 1 : 7).atDay(1);
    }

    public LocalDate finSemestreGratificacion(YearMonth periodo) {
        return YearMonth.of(periodo.getYear(), periodo.getMonthValue() == 7 ? 6 : 12).atEndOfMonth();
    }

    /**
     * Gratificación = remuneración computable x meses/6 x factor del régimen (1.0 general, 0.5 pequeña empresa, 0 micro).
     * Cada falta injustificada del semestre descuenta 1/180 de la gratificación completa. Se suma la bonificación
     * extraordinaria (9%, equivalente al aporte a EsSalud).
     * No lleva descuento de AFP/ONP: la gratificación es inafecta a aportes del trabajador.
     */
    public Gratificacion gratificacion(BigDecimal computable, BigDecimal factor, int meses, long faltasSemestre) {
        if (meses <= 0 || factor.signum() == 0) return Gratificacion.vacia();
        BigDecimal completa = computable.multiply(factor);
        BigDecimal bruta = completa.multiply(new BigDecimal(meses)).divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
        BigDecimal descuento = completa.multiply(new BigDecimal(faltasSemestre)).divide(new BigDecimal("180"), 2, RoundingMode.HALF_UP)
                .min(bruta);
        BigDecimal neta = bruta.subtract(descuento);
        BigDecimal bonif = aplicar(neta, props.getBonificacionExtraordinariaPct());
        return new Gratificacion(meses, bruta, descuento, neta, bonif);
    }

    // ------------------------------------------------------------------ CTS

    /**
     * CTS semestral (depósito de mayo por nov-abr, de noviembre por may-oct).
     * Computable = remuneración + 1/6 de la última gratificación. Monto = computable x (meses x 30 + días - faltas) / 360 x factor.
     * Solo corresponde si hay al menos un mes completo en el semestre.
     */
    public Cts cts(BigDecimal computable, BigDecimal factor, LocalDate fechaIngreso, YearMonth periodo, long faltasSemestre) {
        LocalDate inicio;
        LocalDate fin;
        if (periodo.getMonthValue() == 5) {
            inicio = LocalDate.of(periodo.getYear() - 1, 11, 1);
            fin = LocalDate.of(periodo.getYear(), 4, 30);
        } else {
            inicio = LocalDate.of(periodo.getYear(), 5, 1);
            fin = LocalDate.of(periodo.getYear(), 10, 31);
        }
        LocalDate desde = (fechaIngreso != null && fechaIngreso.isAfter(inicio)) ? fechaIngreso : inicio;
        if (desde.isAfter(fin)) return new Cts(0, 0, BigDecimal.ZERO.setScale(2));

        Period p = Period.between(desde, fin.plusDays(1));
        int meses = p.getYears() * 12 + p.getMonths();
        int dias = p.getDays();
        if (meses < 1 || factor.signum() == 0) return new Cts(meses, dias, BigDecimal.ZERO.setScale(2));

        long totalDias = Math.max(0, meses * 30L + dias - faltasSemestre);
        BigDecimal monto = computable.multiply(new BigDecimal(totalDias)).multiply(factor)
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);
        return new Cts(meses, dias, monto);
    }

    public LocalDate inicioSemestreCts(YearMonth periodo) {
        return periodo.getMonthValue() == 5 ? LocalDate.of(periodo.getYear() - 1, 11, 1) : LocalDate.of(periodo.getYear(), 5, 1);
    }

    public LocalDate finSemestreCts(YearMonth periodo) {
        return periodo.getMonthValue() == 5 ? LocalDate.of(periodo.getYear(), 4, 30) : LocalDate.of(periodo.getYear(), 10, 31);
    }

    // -------------------------------------------------------------- Helpers

    private BigDecimal aplicar(BigDecimal base, BigDecimal tasa) {
        return base.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
    }

    private String pct(BigDecimal fraccion) {
        return fraccion.multiply(CIEN).stripTrailingZeros().toPlainString() + "%";
    }
}
