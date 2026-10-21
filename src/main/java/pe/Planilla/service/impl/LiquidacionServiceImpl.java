package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.LineaBoletaDTO;
import pe.Planilla.model.dto.LiquidacionRequestDTO;
import pe.Planilla.model.dto.LiquidacionResponseDTO;
import pe.Planilla.model.entity.BoletaDetalle;
import pe.Planilla.model.entity.BoletaPago;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.repository.BoletaPagoRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.service.LiquidacionService;
import pe.Planilla.service.calculo.CalculadoraPlanilla;
import pe.Planilla.service.calculo.CalculadoraPlanilla.LineaPension;
import pe.Planilla.service.calculo.CodigosPlame;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Liquidación de beneficios sociales al cese. Cada beneficio se calcula SOLO por el tramo pendiente:
 *  - Vacaciones truncas: desde el último aniversario de ingreso hasta el cese (1/12 por mes, 1/30 de doceavo por día).
 *  - Gratificación trunca: meses completos del semestre en curso (ene-jun / jul-dic) sobre 6, más la bonificación del 9%.
 *  - CTS trunca: desde el último depósito (1 de mayo o 1 de noviembre) hasta el cese, sobre 360 días, con 1/6 de gratificación.
 * Remuneración computable = sueldo + asignación familiar + promedio de variables (horas extras, comisiones, bonos) si se
 * percibieron en al menos 3 meses consecutivos de los últimos 6. Se descuenta AFP/ONP solo de las vacaciones truncas.
 */
@Service
@RequiredArgsConstructor
public class LiquidacionServiceImpl implements LiquidacionService {

    private static final String REGIMEN_RXH = "Cuarta Categoría (RxH)";
    private static final BigDecimal D360 = new BigDecimal("360");
    private static final BigDecimal D30 = new BigDecimal("30");

    private final EmpleadoRepository empleadoRepository;
    private final BoletaPagoRepository boletaRepository;
    private final CalculadoraPlanilla calculadora;
    private final PlanillaProperties props;

    @Override
    @Transactional(readOnly = true)
    public LiquidacionResponseDTO calcularLiquidacion(LiquidacionRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        LocalDate cese = dto.getFechaCese() != null ? dto.getFechaCese() : empleado.getFechaCese();
        if (cese == null) {
            throw new RuntimeException("Indique la fecha de cese.");
        }
        LocalDate ingreso = empleado.getFechaIngreso();
        if (ingreso == null || cese.isBefore(ingreso)) {
            throw new RuntimeException("La fecha de cese no puede ser anterior a la fecha de ingreso.");
        }

        RegimenLaboral regimen = empleado.getRegimenLaboral();
        BigDecimal factor = regimen.getFactorLiquidacion() != null ? regimen.getFactorLiquidacion() : BigDecimal.ONE;
        boolean dependiente = !REGIMEN_RXH.equals(regimen.getNombre());
        List<String> avisos = new ArrayList<>();
        List<LineaBoletaDTO> lineas = new ArrayList<>();

        LiquidacionResponseDTO r = new LiquidacionResponseDTO();
        r.setIdEmpleado(empleado.getIdEmpleado());
        r.setNombresEmpleado(empleado.getNombres() + " " + empleado.getApellidos());
        r.setTipoDocumento(empleado.getTipoDocumento() != null ? empleado.getTipoDocumento() : "DNI");
        r.setDni(empleado.getDni());
        r.setRegimen(regimen.getNombre());
        r.setFechaIngreso(ingreso);
        r.setFechaCese(cese);
        Period servicio = Period.between(ingreso, cese.plusDays(1));
        r.setTiempoServicio(servicio.getYears() + " años, " + servicio.getMonths() + " meses, " + servicio.getDays() + " días");

        if (!dependiente) {
            avisos.add("Recibo por honorarios (4ta categoría): no tiene beneficios sociales.");
            r.setDetalle(lineas);
            r.setAvisos(avisos);
            ponerCeros(r);
            return r;
        }

        // ---- Remuneración computable
        BigDecimal sueldo = calculadora.basicoMensual(empleado.getSueldoBase(), empleado.getHorasJornada()); // básico según la jornada
        BigDecimal asignacion = Boolean.TRUE.equals(empleado.getAsignacionFamiliar()) && Boolean.TRUE.equals(regimen.getAsignacionFamiliar())
                ? calculadora.asignacionFamiliarMensual() : BigDecimal.ZERO;
        BigDecimal promedioVariables = promedioVariables(empleado.getIdEmpleado(), YearMonth.from(cese), avisos);
        BigDecimal computable = sueldo.add(asignacion).add(promedioVariables);
        r.setRemuneracionComputable(computable);
        r.setPromedioVariables(promedioVariables);

        // ---- A. Vacaciones truncas: desde el último aniversario (30 días por año, o los del régimen)
        int diasVacacionesAnio = regimen.getDiasVacaciones() != null ? regimen.getDiasVacaciones() : 30;
        BigDecimal vacaciones = BigDecimal.ZERO;
        String detalleVac = "Sin vacaciones truncas";
        if (diasVacacionesAnio > 0) {
            LocalDate ultimoAniversario = ultimoAniversario(ingreso, cese);
            Period p = Period.between(ultimoAniversario, cese.plusDays(1));
            int meses = p.getYears() * 12 + p.getMonths();
            int dias = p.getDays();
            // Remuneración vacacional del año = computable x (días del régimen / 30); por mes 1/12 y por día 1/360
            BigDecimal vacacionAnual = computable.multiply(new BigDecimal(diasVacacionesAnio)).divide(D30, 4, RoundingMode.HALF_UP);
            vacaciones = vacacionAnual.multiply(new BigDecimal(meses * 30L + dias)).divide(D360, 2, RoundingMode.HALF_UP);
            detalleVac = meses + " meses y " + dias + " días desde el " + ultimoAniversario;
            if (ultimoAniversario.isAfter(ingreso)) {
                avisos.add("Vacaciones: se asume que las de los años anteriores ya fueron gozadas o pagadas (el sistema no lleva el récord vacacional).");
            }
        }
        r.setMontoVacacionesTruncas(vacaciones);
        r.setDetalleVacaciones(detalleVac);
        agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.VACACIONES_TRUNCAS, "Vacaciones truncas (" + detalleVac + ")", vacaciones);

        // ---- B. Gratificación trunca: meses completos del semestre en curso
        BigDecimal grati = BigDecimal.ZERO;
        BigDecimal bonif = BigDecimal.ZERO;
        String detalleGrati = "No corresponde";
        if (factor.signum() > 0) {
            int mesCese = cese.getMonthValue();
            LocalDate inicioSemestre = LocalDate.of(cese.getYear(), mesCese <= 6 ? 1 : 7, 1);
            LocalDate desde = ingreso.isAfter(inicioSemestre) ? ingreso : inicioSemestre;
            int mesesCompletos = mesesCalendarioCompletos(desde, cese);
            if (mesesCompletos > 0) {
                grati = computable.multiply(factor).multiply(new BigDecimal(mesesCompletos)).divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
                bonif = grati.multiply(props.getBonificacionExtraordinariaPct()).setScale(2, RoundingMode.HALF_UP);
                detalleGrati = mesesCompletos + "/6 meses del semestre " + (mesCese <= 6 ? "enero-junio" : "julio-diciembre");
            } else {
                detalleGrati = "Sin mes completo en el semestre (mínimo 1)";
            }
        }
        r.setMontoGratificacionTrunca(grati);
        r.setMontoBonificacionExtraordinaria(bonif);
        r.setDetalleGratificacion(detalleGrati);
        agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.GRATIFICACION_PROPORCIONAL, "Gratificación trunca (" + detalleGrati + ")", grati);
        agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.BONIF_EXTRAORDINARIA_PROPORCIONAL, "Bonificación extraordinaria 9% sobre gratificación trunca", bonif);

        // ---- C. CTS trunca: desde el último depósito (1-may o 1-nov), con 1/6 de la última gratificación
        BigDecimal cts = BigDecimal.ZERO;
        String detalleCts = "No corresponde";
        if (factor.signum() > 0) {
            LocalDate inicioCts = inicioSemestreCts(cese);
            LocalDate desde = ingreso.isAfter(inicioCts) ? ingreso : inicioCts;
            Period p = Period.between(desde, cese.plusDays(1));
            int meses = p.getYears() * 12 + p.getMonths();
            int dias = p.getDays();
            if (meses >= 1 || (meses == 0 && dias >= 15)) { // con 15 días o más ya hay derecho a CTS
                BigDecimal sextoGrati = ultimaGratificacionCompleta(empleado, computable, factor).divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
                BigDecimal computableCts = computable.add(sextoGrati);
                cts = computableCts.multiply(factor).multiply(new BigDecimal(meses * 30L + dias)).divide(D360, 2, RoundingMode.HALF_UP);
                detalleCts = meses + " meses y " + dias + " días desde el " + desde + " (incluye 1/6 de gratificación S/ " + sextoGrati + ")";
            } else {
                detalleCts = "Menos de 15 días desde el último depósito";
            }
        }
        r.setMontoCtsTrunca(cts);
        r.setDetalleCts(detalleCts);
        agregar(lineas, BoletaDetalle.INFORMATIVO, null, "CTS trunca a depositar (" + detalleCts + ")", cts);

        // ---- Totales y descuento de pensión (solo sobre las vacaciones truncas: grati y CTS son inafectas)
        BigDecimal totalBeneficios = vacaciones.add(grati).add(bonif).add(cts);
        BigDecimal pension = BigDecimal.ZERO;
        StringBuilder detallePension = new StringBuilder();
        if (vacaciones.signum() > 0) {
            for (LineaPension l : calculadora.pension(empleado.getSistemaPensionario(), empleado.getAfp(), empleado.getTipoComisionAfp(), vacaciones)) {
                pension = pension.add(l.monto());
                agregar(lineas, BoletaDetalle.DESCUENTO, l.codigo(), l.descripcion() + " sobre vacaciones truncas", l.monto());
                detallePension.append(detallePension.length() > 0 ? "; " : "").append(l.descripcion());
            }
        }
        r.setTotalBeneficios(totalBeneficios);
        r.setDescuentoPension(pension);
        r.setDetallePension(detallePension.toString());
        // La CTS no se entrega en efectivo: va al banco. El neto a pagar al trabajador excluye la CTS.
        r.setTotalNeto(vacaciones.add(grati).add(bonif).subtract(pension));
        avisos.add("El neto excluye la CTS trunca, que se deposita en la cuenta CTS del trabajador. No incluye la remuneración del último mes (va en la boleta mensual) ni descuentos pendientes.");
        avisos.add("Cálculo referencial, no se guarda. Confirmar con el contador antes de pagar.");
        r.setDetalle(lineas);
        r.setAvisos(avisos);
        return r;
    }

    // ------------------------------------------------------------------ ayudas

    private void ponerCeros(LiquidacionResponseDTO r) {
        BigDecimal cero = BigDecimal.ZERO.setScale(2);
        r.setRemuneracionComputable(cero);
        r.setPromedioVariables(cero);
        r.setMontoVacacionesTruncas(cero);
        r.setMontoGratificacionTrunca(cero);
        r.setMontoBonificacionExtraordinaria(cero);
        r.setMontoCtsTrunca(cero);
        r.setTotalBeneficios(cero);
        r.setDescuentoPension(cero);
        r.setTotalNeto(cero);
    }

    private LocalDate ultimoAniversario(LocalDate ingreso, LocalDate cese) {
        LocalDate aniversario = ingreso.withYear(cese.getYear());
        if (aniversario.isAfter(cese)) aniversario = aniversario.minusYears(1);
        return aniversario.isBefore(ingreso) ? ingreso : aniversario;
    }

    /** Meses calendario completos entre dos fechas (el mes cuenta si se trabajó del 1 al último día). */
    private int mesesCalendarioCompletos(LocalDate desde, LocalDate hasta) {
        YearMonth primero = desde.getDayOfMonth() == 1 ? YearMonth.from(desde) : YearMonth.from(desde).plusMonths(1);
        YearMonth ultimo = hasta.getDayOfMonth() == hasta.lengthOfMonth() ? YearMonth.from(hasta) : YearMonth.from(hasta).minusMonths(1);
        if (ultimo.isBefore(primero)) return 0;
        return (int) (primero.until(ultimo, java.time.temporal.ChronoUnit.MONTHS) + 1);
    }

    private LocalDate inicioSemestreCts(LocalDate cese) {
        int m = cese.getMonthValue();
        if (m >= 5 && m <= 10) return LocalDate.of(cese.getYear(), 5, 1);
        return m >= 11 ? LocalDate.of(cese.getYear(), 11, 1) : LocalDate.of(cese.getYear() - 1, 11, 1);
    }

    /** Última gratificación pagada, llevada a régimen completo; si no hay boleta, la teórica. */
    private BigDecimal ultimaGratificacionCompleta(Empleado e, BigDecimal computable, BigDecimal factor) {
        for (BoletaPago b : boletaRepository.findByEmpleado_IdEmpleadoOrderByPlanilla_PeriodoDesc(e.getIdEmpleado())) {
            for (BoletaDetalle d : b.getDetalle()) {
                if (CodigosPlame.GRATIFICACION.equals(d.getCodigo())) {
                    return d.getMonto().divide(factor, 2, RoundingMode.HALF_UP);
                }
            }
        }
        return computable;
    }

    /**
     * Promedio de horas extras, comisiones y bonos de los últimos 6 meses, solo si se percibieron en 3 o más meses
     * consecutivos (criterio de la empresa / regla de regularidad). Sin boletas previas = 0.
     */
    private BigDecimal promedioVariables(Integer idEmpleado, YearMonth mesCese, List<String> avisos) {
        List<BoletaPago> boletas = boletaRepository.findByEmpleado_IdEmpleadoOrderByPlanilla_PeriodoDesc(idEmpleado);
        BigDecimal suma = BigDecimal.ZERO;
        int consecutivos = 0, maxConsecutivos = 0, mesesConVariable = 0;
        for (int i = 1; i <= 6; i++) {
            YearMonth ym = mesCese.minusMonths(i);
            String periodo = ym.toString();
            BigDecimal variable = boletas.stream()
                    .filter(b -> periodo.equals(b.getPlanilla().getPeriodo()))
                    .flatMap(b -> b.getDetalle().stream())
                    .filter(d -> BoletaDetalle.INGRESO.equals(d.getTipo()) && esVariable(d.getCodigo()))
                    .map(BoletaDetalle::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (variable.signum() > 0) {
                suma = suma.add(variable);
                mesesConVariable++;
                consecutivos++;
                maxConsecutivos = Math.max(maxConsecutivos, consecutivos);
            } else {
                consecutivos = 0;
            }
        }
        if (maxConsecutivos >= 3) {
            avisos.add("Se incluyó el promedio de ingresos variables de los últimos 6 meses (" + mesesConVariable + " meses con variables).");
            return suma.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2);
    }

    /** Variables: todo ingreso que no sea básico, asignación familiar, vacaciones ni gratificación. */
    private boolean esVariable(String codigo) {
        if (codigo == null) return false;
        return !List.of(CodigosPlame.BASICO, CodigosPlame.ASIGNACION_FAMILIAR, CodigosPlame.VACACIONES,
                CodigosPlame.GRATIFICACION, CodigosPlame.BONIF_EXTRAORDINARIA).contains(codigo);
    }

    private void agregar(List<LineaBoletaDTO> lineas, String tipo, String codigo, String descripcion, BigDecimal monto) {
        if (monto == null || monto.signum() == 0) return;
        lineas.add(new LineaBoletaDTO(tipo, codigo, descripcion, monto.setScale(2, RoundingMode.HALF_UP)));
    }
}
