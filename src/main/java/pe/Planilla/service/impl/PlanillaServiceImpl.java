package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.dto.LineaBoletaDTO;
import pe.Planilla.model.entity.*;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.*;
import pe.Planilla.service.AsistenciaService;
import pe.Planilla.service.PlanillaService;
import pe.Planilla.service.RentaQuintaService;
import pe.Planilla.service.RentaQuintaService.DatosRenta5ta;
import pe.Planilla.service.calculo.CalculadoraAsistencia;
import pe.Planilla.service.calculo.CalculadoraAsistencia.DescuentoTardanzas;
import pe.Planilla.service.calculo.CalculadoraPlanilla;
import pe.Planilla.service.calculo.CalculadoraPlanilla.Cts;
import pe.Planilla.service.calculo.CalculadoraPlanilla.Gratificacion;
import pe.Planilla.service.calculo.CalculadoraPlanilla.LineaPension;
import pe.Planilla.service.calculo.CodigosPlame;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma la boleta de un empleado en un periodo: lee asistencia, movimientos y boletas previas, aplica las reglas de
 * {@link CalculadoraPlanilla} / {@link RentaQuintaService} y guarda la boleta con todas sus líneas.
 */
@Service
@RequiredArgsConstructor
public class PlanillaServiceImpl implements PlanillaService {

    private static final String REGIMEN_RXH = "Cuarta Categoría (RxH)";

    private final EmpleadoRepository empleadoRepository;
    private final PlanillaCabeceraRepository planillaRepository;
    private final BoletaPagoRepository boletaRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final AsistenciaDiariaRepository asistenciaRepository;
    private final AsistenciaService asistenciaService;
    private final RentaQuintaService rentaQuintaService;
    private final CalculadoraPlanilla calculadora;
    private final CalculadoraAsistencia calculadoraAsistencia;
    private final ReglaTardanzaRepository reglaTardanzaRepository;
    private final ConceptoRemunerativoRepository conceptoRepository;
    private final PlanillaProperties props;
    private final NotificadorTiempoReal notificador;

    @Override
    @Transactional // Si algo falla en el cálculo, se revierte todo (No hay boletas a medias)
    public BoletaGeneradaDTO procesarBoletaMensual(Integer idEmpleado, String periodo) {
        validarPeriodo(periodo);

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        YearMonth ym = YearMonth.parse(periodo);
        int anio = ym.getYear();
        int mes = ym.getMonthValue();
        LocalDate inicioMes = ym.atDay(1);
        LocalDate finMes = ym.atEndOfMonth();

        if (empleado.getFechaIngreso() != null && empleado.getFechaIngreso().isAfter(finMes)) {
            throw new RuntimeException("El empleado ingresó el " + empleado.getFechaIngreso() + ", después del periodo " + periodo + ".");
        }
        if (empleado.getFechaCese() != null && empleado.getFechaCese().isBefore(inicioMes)) {
            throw new RuntimeException("El empleado cesó el " + empleado.getFechaCese() + ", antes del periodo " + periodo + ".");
        }

        // 1. Planilla cabecera del mes
        PlanillaCabecera planilla = planillaRepository.findByPeriodo(periodo)
                .orElseGet(() -> {
                    PlanillaCabecera nueva = new PlanillaCabecera();
                    nueva.setPeriodo(periodo);
                    nueva.setEstado("BORRADOR");
                    return planillaRepository.save(nueva);
                });
        if ("CERRADA".equals(planilla.getEstado())) {
            throw new RuntimeException("La planilla " + periodo + " está cerrada y no se puede recalcular.");
        }

        RegimenLaboral regimen = empleado.getRegimenLaboral();
        boolean dependiente = !REGIMEN_RXH.equals(regimen.getNombre()); // RxH (4ta categoría) no tiene AFP/ONP/5ta/grati/CTS
        BigDecimal factor = regimen.getFactorLiquidacion() != null ? regimen.getFactorLiquidacion() : BigDecimal.ONE;
        Integer horasJornada = empleado.getHorasJornada();
        // Sueldo del mes según la jornada (media jornada = sueldo / 8 x 4 horas), como la planilla de la contadora
        BigDecimal basicoMensual = calculadora.basicoMensual(empleado.getSueldoBase(), horasJornada);

        // 2. Días: 30 comerciales, menos faltas injustificadas y días de vacaciones (que se pagan como movimiento)
        long faltas = asistenciaRepository.countByEmpleado_IdEmpleadoAndEstadoAndJustificadaFalseAndFechaBetween(idEmpleado, "FALTA", inicioMes, finMes);
        List<MovimientoPlanilla> movimientos = movimientoRepository.findByEmpleado_IdEmpleadoAndPeriodo(idEmpleado, periodo);
        int diasVacaciones = movimientos.stream()
                .filter(m -> CodigosPlame.VACACIONES.equals(m.getConcepto().getCodigo()))
                .mapToInt(m -> m.getCantidad() != null ? m.getCantidad() : 0)
                .sum();
        int diasPeriodo = calculadora.diasDelPeriodo(empleado.getFechaIngreso(), empleado.getFechaCese(), ym);
        int diasPagados = calculadora.diasPagados(diasPeriodo, faltas, diasVacaciones);

        List<BoletaDetalle> lineas = new ArrayList<>();

        // 3. INGRESOS: sueldo proporcional, asignación familiar, movimientos del mes
        BigDecimal sueldoPagado = calculadora.prorratear(basicoMensual, diasPagados);
        String detalleJornada = horasJornada != null && horasJornada < 8 ? ", jornada de " + horasJornada + " h" : "";
        agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.BASICO, "Remuneración básica (" + diasPagados + " días" + detalleJornada + ")", sueldoPagado);

        BigDecimal asignacionMensual = BigDecimal.ZERO;
        BigDecimal asignacionPagada = BigDecimal.ZERO;
        // Asignación familiar: el empleado la tiene marcada Y su régimen la paga (solo el Régimen General)
        if (dependiente && Boolean.TRUE.equals(empleado.getAsignacionFamiliar())
                && Boolean.TRUE.equals(regimen.getAsignacionFamiliar())) {
            asignacionMensual = calculadora.asignacionFamiliarMensual();
            asignacionPagada = calculadora.prorratear(asignacionMensual, diasPagados);
            agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.ASIGNACION_FAMILIAR, "Asignación familiar", asignacionPagada);
        }

        // Movimientos del mes. Los ingresos "no remunerativos" (condición de trabajo: movilidad, capacitación, bono) se pagan
        // pero no entran a pensiones/EsSalud (afectaEssalud = false) ni a Renta de 5ta (afectaRenta5ta = false).
        BigDecimal ingresosAfectos = BigDecimal.ZERO;
        BigDecimal ingresosNoAfectos5ta = BigDecimal.ZERO;
        for (MovimientoPlanilla mov : movimientos) {
            ConceptoRemunerativo c = mov.getConcepto();
            if ("INGRESO".equals(c.getTipo())) {
                agregar(lineas, BoletaDetalle.INGRESO, c.getCodigo(), c.getDescripcion(), mov.getMonto());
                if (!Boolean.FALSE.equals(c.getAfectaEssalud())) ingresosAfectos = ingresosAfectos.add(mov.getMonto());
                if (Boolean.FALSE.equals(c.getAfectaRenta5ta())) ingresosNoAfectos5ta = ingresosNoAfectos5ta.add(mov.getMonto());
            } else if ("DESCUENTO".equals(c.getTipo())) {
                agregar(lineas, BoletaDetalle.DESCUENTO, c.getCodigo(), c.getDescripcion(), mov.getMonto());
            }
        }

        // Horas extras autorizadas en la asistencia (las 2 primeras de cada día al 25%, el resto al 35%).
        // Valor hora = básico del mes / 30 días / horas de la jornada.
        int[] he = asistenciaService.horasExtraAutorizadasDelMes(idEmpleado, mes, anio);
        if (he[0] + he[1] > 0) {
            BigDecimal valorHora = calculadora.valorHora(basicoMensual, horasJornada);
            if (he[0] > 0) {
                BigDecimal m25 = valorHora.multiply(new BigDecimal("1.25")).multiply(new BigDecimal(he[0])).setScale(2, RoundingMode.HALF_UP);
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.HORAS_EXTRAS_25, "Horas extras 25% (" + he[0] + " h, asistencia)", m25);
                ingresosAfectos = ingresosAfectos.add(m25);
            }
            if (he[1] > 0) {
                BigDecimal m35 = valorHora.multiply(new BigDecimal("1.35")).multiply(new BigDecimal(he[1])).setScale(2, RoundingMode.HALF_UP);
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.HORAS_EXTRAS_35, "Horas extras 35% (" + he[1] + " h, asistencia)", m35);
                ingresosAfectos = ingresosAfectos.add(m35);
            }
        }

        // 4. Tardanzas: tabla del comunicado de la empresa (monto fijo por tramo o medio día) y un día adicional
        // al llegar a N tardanzas en el mes. Las justificadas con documentación no se descuentan. Como en la planilla
        // de la contadora, el descuento REDUCE la remuneración afecta (pensión, EsSalud y Renta de 5ta se calculan sin él).
        List<Integer> tardanzas = asistenciaService.minutosTardanzaInjustificadaDelMes(idEmpleado, mes, anio);
        DescuentoTardanzas dt = calculadoraAsistencia.descuentoTardanzas(
                tardanzas, basicoMensual, reglaTardanzaRepository.findAllByOrderByDesdeMinutoAsc());
        agregar(lineas, BoletaDetalle.DESCUENTO, null, "Descuento por tardanzas (" + dt.cantidad() + ")", dt.montoTramos());
        agregar(lineas, BoletaDetalle.DESCUENTO, null,
                "Descuento de 1 día adicional (" + dt.cantidad() + " tardanzas en el mes)", dt.montoDiaAdicional());
        BigDecimal descuentoTardanzas = dt.montoTramos().add(dt.montoDiaAdicional());

        // Remuneración sobre la que se aporta a pensiones y EsSalud (la gratificación NO entra: es inafecta)
        BigDecimal baseAfecta = sueldoPagado.add(asignacionPagada).add(ingresosAfectos).subtract(descuentoTardanzas).max(BigDecimal.ZERO);

        // 5. GRATIFICACIÓN (julio y diciembre) + bonificación extraordinaria.
        // Computable = básico + asignación familiar + promedio del semestre de horas extras y comisiones (suma / 6).
        BigDecimal fijaMensual = basicoMensual.add(asignacionMensual);
        if (dependiente && (mes == 7 || mes == 12)) {
            DetalleGratificacion g = detalleGratificacion(empleado, ym, factor, basicoMensual, asignacionMensual, sumaVariables(lineas));
            if (g.neta().signum() > 0) {
                String promedio = g.promedioVariable().signum() > 0 ? ", con promedio de variables S/ " + g.promedioVariable() : "";
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.GRATIFICACION,
                        "Gratificación " + (mes == 7 ? "Fiestas Patrias" : "Navidad") + " (" + g.meses() + "/6 meses" + promedio + ")", g.neta());
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.BONIF_EXTRAORDINARIA,
                        "Bonificación extraordinaria (" + props.getBonificacionExtraordinariaPct().movePointRight(2).stripTrailingZeros().toPlainString() + "%)",
                        g.bonificacion());
            }
        }

        // 6. DESCUENTOS: pensión (ONP / AFP / ninguno) y Renta de 5ta
        BigDecimal renta5ta = BigDecimal.ZERO;
        BigDecimal essaludEmpleador = BigDecimal.ZERO;
        BigDecimal depositoCts = BigDecimal.ZERO;

        if (dependiente) {
            for (LineaPension l : calculadora.pension(empleado.getSistemaPensionario(), empleado.getAfp(),
                    empleado.getTipoComisionAfp(), baseAfecta)) {
                agregar(lineas, BoletaDetalle.DESCUENTO, l.codigo(), l.descripcion(), l.monto());
            }

            // Ingreso del mes afecto a 5ta: todo lo pagado, menos lo no remunerativo y menos las tardanzas
            BigDecimal ingresoAfecto5ta = suma(lineas, BoletaDetalle.INGRESO).subtract(ingresosNoAfectos5ta)
                    .subtract(descuentoTardanzas).max(BigDecimal.ZERO);
            renta5ta = calcularRenta5ta(empleado, ym, factor, fijaMensual, ingresoAfecto5ta);
            agregar(lineas, BoletaDetalle.DESCUENTO, CodigosPlame.RENTA_5TA, "Renta de 5ta categoría", renta5ta);

            // 7. Costos del empleador (informativos, no restan al neto)
            essaludEmpleador = calculadora.essaludEmpleador(baseAfecta);
            agregar(lineas, BoletaDetalle.INFORMATIVO, CodigosPlame.ESSALUD,
                    "EsSalud empleador (" + props.getEssaludPct().movePointRight(2).stripTrailingZeros().toPlainString() + "%)", essaludEmpleador);

            // CTS en mayo y noviembre (a tiempo parcial, menos de 4 horas, no corresponde)
            if ((mes == 5 || mes == 11) && factor.signum() > 0 && calculadora.tieneCts(horasJornada)) {
                DetalleCts cts = detalleCts(empleado, ym, factor, basicoMensual, asignacionMensual);
                if (cts.monto().signum() > 0) {
                    agregar(lineas, BoletaDetalle.INFORMATIVO, null,
                            "CTS a depositar (" + cts.meses() + " meses y " + cts.dias() + " días)", cts.monto());
                }
                depositoCts = cts.monto();
            }
        }

        // 7. Totales
        BigDecimal totalIngresos = suma(lineas, BoletaDetalle.INGRESO);
        BigDecimal totalDescuentos = suma(lineas, BoletaDetalle.DESCUENTO);
        BigDecimal totalNeto = totalIngresos.subtract(totalDescuentos);

        // 8. Guardar (si ya existía para este empleado y periodo, se recalcula en vez de duplicarla)
        BoletaPago boleta = boletaRepository
                .findByPlanilla_PeriodoAndEmpleado_IdEmpleado(periodo, idEmpleado)
                .orElseGet(BoletaPago::new);
        boolean esNueva = boleta.getIdBoleta() == null;

        boleta.setPlanilla(planilla);
        boleta.setEmpleado(empleado);
        boleta.setTotalIngresos(totalIngresos);
        boleta.setTotalDescuentos(totalDescuentos);
        boleta.setTotalNeto(totalNeto);
        boleta.setRenta5taRetenida(renta5ta);
        boleta.setDiasTrabajados(diasPagados);
        boleta.setEssaludEmpleador(essaludEmpleador);
        boleta.setDepositoCts(depositoCts);

        boleta.getDetalle().clear();
        int orden = 0;
        for (BoletaDetalle d : lineas) {
            d.setBoleta(boleta);
            d.setOrden(++orden);
            boleta.getDetalle().add(d);
        }

        BoletaPago guardada = boletaRepository.save(boleta);

        BoletaGeneradaDTO dto = convertirADTO(guardada);
        notificador.publicar("boletas", esNueva ? "CREADO" : "ACTUALIZADO", dto.getIdBoleta(), dto);
        return dto;
    }

    /**
     * Retención de 5ta (procedimiento SUNAT): acumula lo percibido y lo retenido en el año. Para los meses anteriores SIN
     * boleta (el sistema se empezó a usar a mitad de año) se estima el ingreso con la remuneración actual Y se simula la
     * retención que ese mes habría hecho el mismo procedimiento; si no, el impuesto de todo el año se repartiría solo en
     * los meses que quedan (en setiembre salía 777.58 en vez de 259.19 para un sueldo de 5 113 constante).
     */
    private BigDecimal calcularRenta5ta(Empleado empleado, YearMonth ym, BigDecimal factor, BigDecimal fijaMensual, BigDecimal ingresoMesActual) {
        int anio = ym.getYear();
        int mes = ym.getMonthValue();
        List<BoletaPago> delAnio = boletaRepository
                .findByPlanilla_PeriodoStartingWithAndEmpleado_IdEmpleado(anio + "-", empleado.getIdEmpleado());

        BigDecimal[] ingreso = new BigDecimal[13];
        BigDecimal[] retencion = new BigDecimal[13];
        for (int m = 1; m < mes; m++) {
            String per = String.format("%d-%02d", anio, m);
            BoletaPago b = delAnio.stream().filter(x -> per.equals(x.getPlanilla().getPeriodo())).findFirst().orElse(null);
            if (b != null) {
                ingreso[m] = ingresoAfecto5ta(b);
                retencion[m] = b.getRenta5taRetenida() != null ? b.getRenta5taRetenida() : BigDecimal.ZERO;
            } else {
                ingreso[m] = estimarIngresoMes(empleado, YearMonth.of(anio, m), fijaMensual, factor);
                retencion[m] = ingreso[m].signum() > 0
                        ? rentaQuintaService.calcularRetencionMensual(new DatosRenta5ta(m, suma(ingreso, 1, m - 1), ingreso[m],
                                fijaMensual, factor, suma(retencion, 1, rentaQuintaService.mesCorteRetenciones(m))))
                        : BigDecimal.ZERO;
            }
        }
        BigDecimal ingresosPrevios = suma(ingreso, 1, mes - 1);
        BigDecimal retencionesPrevias = suma(retencion, 1, rentaQuintaService.mesCorteRetenciones(mes));
        return rentaQuintaService.calcularRetencionMensual(
                new DatosRenta5ta(mes, ingresosPrevios, ingresoMesActual, fijaMensual, factor, retencionesPrevias));
    }

    private BigDecimal suma(BigDecimal[] porMes, int desde, int hasta) {
        BigDecimal s = BigDecimal.ZERO;
        for (int m = desde; m <= hasta && m < porMes.length; m++) {
            if (porMes[m] != null) s = s.add(porMes[m]);
        }
        return s;
    }

    /** Ingreso de una boleta que cuenta para la 5ta: ingresos menos los no afectos (condición de trabajo) y menos las tardanzas. */
    private BigDecimal ingresoAfecto5ta(BoletaPago b) {
        BigDecimal total = BigDecimal.ZERO;
        for (BoletaDetalle d : b.getDetalle()) {
            if (BoletaDetalle.INGRESO.equals(d.getTipo())) {
                boolean afecto = d.getCodigo() == null || conceptoRepository.findByCodigo(d.getCodigo())
                        .map(c -> !Boolean.FALSE.equals(c.getAfectaRenta5ta())).orElse(true);
                if (afecto) total = total.add(d.getMonto());
            } else if (BoletaDetalle.DESCUENTO.equals(d.getTipo()) && d.getCodigo() == null) {
                total = total.subtract(d.getMonto()); // tardanzas (únicas líneas de descuento sin código)
            }
        }
        return total.max(BigDecimal.ZERO);
    }

    private BigDecimal estimarIngresoMes(Empleado empleado, YearMonth ym, BigDecimal fijaMensual, BigDecimal factor) {
        int dias = calculadora.diasDelPeriodo(empleado.getFechaIngreso(), ym);
        if (dias == 0) return BigDecimal.ZERO;
        BigDecimal ingreso = calculadora.prorratear(fijaMensual, dias);
        if (ym.getMonthValue() == 7) {
            Gratificacion g = calculadora.gratificacion(fijaMensual, factor,
                    calculadora.mesesComputablesGratificacion(empleado.getFechaIngreso(), ym), 0);
            ingreso = ingreso.add(g.neta()).add(g.bonificacionExtraordinaria());
        }
        return ingreso;
    }

    // ------------------------------------------------------- gratificación y CTS (desglose para boleta y constancias)

    @Override
    @Transactional(readOnly = true)
    public DetalleGratificacion detalleGratificacion(Integer idEmpleado, String periodo) {
        validarPeriodo(periodo);
        YearMonth ym = YearMonth.parse(periodo);
        if (ym.getMonthValue() != 7 && ym.getMonthValue() != 12) {
            throw new RuntimeException("La gratificación se liquida en julio y diciembre (periodo " + periodo + ").");
        }
        Empleado e = empleadoRepository.findById(idEmpleado).orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        BigDecimal factor = e.getRegimenLaboral().getFactorLiquidacion() != null ? e.getRegimenLaboral().getFactorLiquidacion() : BigDecimal.ONE;
        BigDecimal basico = calculadora.basicoMensual(e.getSueldoBase(), e.getHorasJornada());
        // Variables del propio mes (diciembre pertenece a su semestre): las de la boleta ya procesada, si existe
        BigDecimal variablesMes = boletaRepository.findByPlanilla_PeriodoAndEmpleado_IdEmpleado(periodo, idEmpleado)
                .map(b -> sumaVariables(b.getDetalle())).orElse(BigDecimal.ZERO);
        return detalleGratificacion(e, ym, factor, basico, asignacionMensual(e), variablesMes);
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleCts detalleCts(Integer idEmpleado, String periodo) {
        validarPeriodo(periodo);
        YearMonth ym = YearMonth.parse(periodo);
        if (ym.getMonthValue() != 5 && ym.getMonthValue() != 11) {
            throw new RuntimeException("La CTS se deposita en mayo y noviembre (periodo " + periodo + ").");
        }
        Empleado e = empleadoRepository.findById(idEmpleado).orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        BigDecimal factor = e.getRegimenLaboral().getFactorLiquidacion() != null ? e.getRegimenLaboral().getFactorLiquidacion() : BigDecimal.ONE;
        if (!calculadora.tieneCts(e.getHorasJornada())) {
            throw new RuntimeException("El trabajador tiene jornada menor a 4 horas: no le corresponde CTS.");
        }
        return detalleCts(e, ym, factor, calculadora.basicoMensual(e.getSueldoBase(), e.getHorasJornada()), asignacionMensual(e));
    }

    /** Asignación familiar mensual que le toca (la tiene marcada y su régimen la paga); 0 si no. */
    private BigDecimal asignacionMensual(Empleado e) {
        boolean dependiente = !REGIMEN_RXH.equals(e.getRegimenLaboral().getNombre());
        if (dependiente && Boolean.TRUE.equals(e.getAsignacionFamiliar()) && Boolean.TRUE.equals(e.getRegimenLaboral().getAsignacionFamiliar())) {
            return calculadora.asignacionFamiliarMensual();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Gratificación del semestre: computable = básico + asignación + promedio (suma / 6) de horas extras y comisiones del
     * semestre; meses completos; 1/180 por falta injustificada; bonificación extraordinaria 9%.
     */
    private DetalleGratificacion detalleGratificacion(Empleado e, YearMonth ym, BigDecimal factor, BigDecimal basico,
                                                      BigDecimal asignacion, BigDecimal variablesMesActual) {
        LocalDate desde = calculadora.inicioSemestreGratificacion(ym);
        LocalDate hasta = calculadora.finSemestreGratificacion(ym);
        int meses = calculadora.mesesComputablesGratificacion(e.getFechaIngreso(), ym);
        long faltas = asistenciaRepository.countByEmpleado_IdEmpleadoAndEstadoAndJustificadaFalseAndFechaBetween(e.getIdEmpleado(), "FALTA", desde, hasta);
        BigDecimal promedio = calculadora.promedioSemestral(sumaVariables(e.getIdEmpleado(), desde, hasta, ym, variablesMesActual));
        BigDecimal computable = basico.add(asignacion).add(promedio);
        Gratificacion g = calculadora.gratificacion(computable, factor, meses, faltas);
        LocalDate desdeReal = e.getFechaIngreso() != null && e.getFechaIngreso().isAfter(desde) ? e.getFechaIngreso() : desde;
        return new DetalleGratificacion(ym.toString(), desdeReal, hasta, meses, basico, asignacion, promedio, computable, factor,
                g.bruta(), faltas, g.descuentoFaltas(), g.neta(), g.bonificacionExtraordinaria(), g.neta().add(g.bonificacionExtraordinaria()));
    }

    /**
     * CTS de mayo (nov-abr) o noviembre (may-oct): computable = básico + asignación + promedio de horas extras y comisiones
     * del semestre + 1/6 de la última gratificación. Monto = computable / 12 por mes y / 360 por día. Se informa, no se paga en la boleta.
     */
    private DetalleCts detalleCts(Empleado e, YearMonth ym, BigDecimal factor, BigDecimal basico, BigDecimal asignacion) {
        int mes = ym.getMonthValue();
        String periodoGrati = mes == 5 ? (ym.getYear() - 1) + "-12" : ym.getYear() + "-07";
        LocalDate desde = calculadora.inicioSemestreCts(ym);
        LocalDate hasta = calculadora.finSemestreCts(ym);
        BigDecimal fijaMensual = basico.add(asignacion);

        // 1/6 de la última gratificación pagada (llevada a régimen completo; el factor se aplica al final); si no hay boleta, la teórica
        BigDecimal gratiBase = boletaRepository
                .findByPlanilla_PeriodoAndEmpleado_IdEmpleado(periodoGrati, e.getIdEmpleado())
                .flatMap(b -> b.getDetalle().stream()
                        .filter(d -> CodigosPlame.GRATIFICACION.equals(d.getCodigo()))
                        .map(BoletaDetalle::getMonto)
                        .findFirst())
                .map(monto -> factor.signum() > 0 ? monto.divide(factor, 2, RoundingMode.HALF_UP) : monto)
                .orElse(fijaMensual);
        BigDecimal sextoGrati = gratiBase.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
        BigDecimal promedio = calculadora.promedioSemestral(sumaVariables(e.getIdEmpleado(), desde, hasta, ym, BigDecimal.ZERO));
        BigDecimal computable = fijaMensual.add(promedio).add(sextoGrati);

        long faltas = asistenciaRepository.countByEmpleado_IdEmpleadoAndEstadoAndJustificadaFalseAndFechaBetween(e.getIdEmpleado(), "FALTA", desde, hasta);
        Cts cts = calculadora.cts(computable, factor, e.getFechaIngreso(), ym, faltas);
        LocalDate desdeReal = e.getFechaIngreso() != null && e.getFechaIngreso().isAfter(desde) ? e.getFechaIngreso() : desde;
        return new DetalleCts(ym.toString(), desdeReal, hasta, cts.meses(), cts.dias(), basico, asignacion, promedio, gratiBase,
                sextoGrati, computable, factor, faltas, cts.monto());
    }

    /** Suma de horas extras y comisiones pagadas en las boletas de los meses [desde, hasta]; el mes en curso usa sus propias líneas. */
    private BigDecimal sumaVariables(Integer idEmpleado, LocalDate desde, LocalDate hasta, YearMonth actual, BigDecimal variablesActual) {
        BigDecimal suma = BigDecimal.ZERO;
        for (YearMonth m = YearMonth.from(desde); !m.isAfter(YearMonth.from(hasta)); m = m.plusMonths(1)) {
            if (m.equals(actual)) {
                suma = suma.add(variablesActual);
                continue;
            }
            suma = suma.add(boletaRepository.findByPlanilla_PeriodoAndEmpleado_IdEmpleado(m.toString(), idEmpleado)
                    .map(b -> sumaVariables(b.getDetalle())).orElse(BigDecimal.ZERO));
        }
        return suma;
    }

    private BigDecimal sumaVariables(List<BoletaDetalle> lineas) {
        return lineas.stream()
                .filter(l -> BoletaDetalle.INGRESO.equals(l.getTipo()) && l.getCodigo() != null
                        && CodigosPlame.VARIABLES_PROMEDIABLES.contains(l.getCodigo()))
                .map(BoletaDetalle::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ----------------------------------------------------------------- quincena

    @Override
    @Transactional(readOnly = true)
    public List<Quincena> calcularQuincena(String periodo) {
        validarPeriodo(periodo);
        YearMonth ym = YearMonth.parse(periodo);
        LocalDate dia15 = ym.atDay(15);
        List<Quincena> filas = new ArrayList<>();
        for (Empleado e : empleadoRepository.findAll()) {
            if (e.getFechaIngreso() != null && e.getFechaIngreso().isAfter(dia15)) continue;   // entra después de la quincena
            if (e.getFechaCese() != null && e.getFechaCese().isBefore(dia15)) continue;        // cesó antes
            if (!"ACTIVO".equals(e.getEstado()) && e.getFechaCese() == null) continue;
            filas.add(quincenaDe(e, ym));
        }
        filas.sort(java.util.Comparator.comparing(Quincena::empleado, String.CASE_INSENSITIVE_ORDER));
        return filas;
    }

    /**
     * Adelanto = (básico + asignación) / 2 − pensión sobre esa mitad − mitad de la Renta de 5ta del mes anterior,
     * como la columna "1RA QUINC." de la planilla de la contadora.
     */
    private Quincena quincenaDe(Empleado e, YearMonth ym) {
        boolean dependiente = !REGIMEN_RXH.equals(e.getRegimenLaboral().getNombre());
        BigDecimal basico = calculadora.basicoMensual(e.getSueldoBase(), e.getHorasJornada());
        BigDecimal asignacion = asignacionMensual(e);
        BigDecimal dos = new BigDecimal("2");
        BigDecimal mitad = basico.add(asignacion).divide(dos, 2, RoundingMode.HALF_UP);
        BigDecimal pension = BigDecimal.ZERO;
        if (dependiente) {
            for (LineaPension l : calculadora.pension(e.getSistemaPensionario(), e.getAfp(), e.getTipoComisionAfp(), mitad)) {
                pension = pension.add(l.monto());
            }
        }
        BigDecimal renta = boletaRepository.findByPlanilla_PeriodoAndEmpleado_IdEmpleado(ym.minusMonths(1).toString(), e.getIdEmpleado())
                .map(BoletaPago::getRenta5taRetenida)
                .filter(java.util.Objects::nonNull)
                .orElse(BigDecimal.ZERO)
                .divide(dos, 2, RoundingMode.HALF_UP);
        BigDecimal adelanto = mitad.subtract(pension).subtract(renta).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean registrado = movimientoRepository
                .findFirstByEmpleado_IdEmpleadoAndPeriodoAndConcepto_Codigo(e.getIdEmpleado(), ym.toString(), CodigosPlame.ADELANTO).isPresent();
        return new Quincena(e.getIdEmpleado(), e.getNombres() + " " + e.getApellidos(),
                e.getTipoDocumento() != null ? e.getTipoDocumento() : "DNI", e.getDni(), e.getBanco(), e.getCuentaBancaria(),
                basico, asignacion, pension, renta, adelanto, registrado);
    }

    @Override
    @Transactional
    public List<Quincena> registrarQuincena(String periodo) {
        validarPeriodo(periodo);
        planillaRepository.findByPeriodo(periodo)
                .filter(p -> PlanillaCabecera.CERRADA.equals(p.getEstado()))
                .ifPresent(p -> {
                    throw new RuntimeException("La planilla " + periodo + " está cerrada: no se puede registrar la quincena.");
                });
        ConceptoRemunerativo adelanto = conceptoRepository.findByCodigo(CodigosPlame.ADELANTO)
                .orElseThrow(() -> new RuntimeException("Falta el concepto 0701 (adelanto de remuneración): reinicia el sistema para sembrarlo."));
        List<Quincena> resultado = new ArrayList<>();
        for (Quincena q : calcularQuincena(periodo)) {
            if (q.adelanto().signum() <= 0) {
                resultado.add(q);
                continue;
            }
            MovimientoPlanilla m = movimientoRepository
                    .findFirstByEmpleado_IdEmpleadoAndPeriodoAndConcepto_Codigo(q.idEmpleado(), periodo, CodigosPlame.ADELANTO)
                    .orElseGet(() -> {
                        MovimientoPlanilla nuevo = new MovimientoPlanilla();
                        nuevo.setEmpleado(empleadoRepository.getReferenceById(q.idEmpleado()));
                        nuevo.setConcepto(adelanto);
                        nuevo.setPeriodo(periodo);
                        return nuevo;
                    });
            m.setMonto(q.adelanto());
            MovimientoPlanilla guardado = movimientoRepository.save(m);
            // Mismo formato que la pantalla de movimientos espera en vivo
            notificador.publicar("movimientos", "CREADO", guardado.getIdMovimiento(), java.util.Map.of(
                    "idMovimiento", guardado.getIdMovimiento(), "idEmpleado", q.idEmpleado(), "empleado", q.empleado(),
                    "codigoConcepto", adelanto.getCodigo(), "concepto", adelanto.getDescripcion(), "tipo", "DESCUENTO",
                    "periodo", periodo, "monto", q.adelanto()));
            resultado.add(new Quincena(q.idEmpleado(), q.empleado(), q.tipoDocumento(), q.documento(), q.banco(), q.cuenta(),
                    q.basico(), q.asignacionFamiliar(), q.pension(), q.renta5ta(), q.adelanto(), true));
        }
        return resultado;
    }

    @Override
    @Transactional
    public List<BoletaGeneradaDTO> procesarPlanilla(String periodo) {
        validarPeriodo(periodo);
        LocalDate finMes = YearMonth.parse(periodo).atEndOfMonth();
        LocalDate inicioMes = YearMonth.parse(periodo).atDay(1);
        // Empleados que ya habían ingresado en el periodo y que no habían cesado antes de que empiece
        // (un cesado a mitad de mes sí entra: cobra los días trabajados de ese mes)
        List<Empleado> aProcesar = empleadoRepository.findAll().stream()
                .filter(e -> e.getFechaIngreso() == null || !e.getFechaIngreso().isAfter(finMes))
                .filter(e -> e.getFechaCese() == null || !e.getFechaCese().isBefore(inicioMes))
                .toList();
        if (aProcesar.isEmpty()) {
            throw new RuntimeException("No hay empleados para procesar en el periodo " + periodo + ".");
        }
        return aProcesar.stream()
                .map(e -> procesarBoletaMensual(e.getIdEmpleado(), periodo))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BoletaGeneradaDTO obtenerBoleta(Integer idBoleta) {
        return boletaRepository.findById(idBoleta).map(this::convertirADTO)
                .orElseThrow(() -> new RuntimeException("Boleta no encontrada."));
    }

    @Override
    public PlanillaCabecera estadoPlanilla(String periodo) {
        validarPeriodo(periodo);
        return planillaRepository.findByPeriodo(periodo).orElse(null);
    }

    @Override
    @Transactional
    public PlanillaCabecera cerrar(String periodo, String usuario) {
        validarPeriodo(periodo);
        PlanillaCabecera p = planillaRepository.findByPeriodo(periodo)
                .orElseThrow(() -> new RuntimeException("La planilla " + periodo + " todavía no se ha procesado."));
        if (PlanillaCabecera.CERRADA.equals(p.getEstado())) {
            throw new RuntimeException("La planilla " + periodo + " ya está cerrada.");
        }
        if (boletaRepository.findByPlanilla_Periodo(periodo).isEmpty()) {
            throw new RuntimeException("La planilla " + periodo + " no tiene boletas: procésala antes de cerrarla.");
        }
        p.setEstado(PlanillaCabecera.CERRADA);
        p.setFechaCierre(LocalDate.now());
        p.setCerradaPor(usuario);
        if (p.getFechaPago() == null) p.setFechaPago(YearMonth.parse(periodo).atEndOfMonth());
        PlanillaCabecera guardada = planillaRepository.save(p);
        notificador.publicar("planillas", "CERRADA", guardada.getIdPlanilla(), guardada);
        return guardada;
    }

    @Override
    @Transactional
    public PlanillaCabecera reabrir(String periodo, String usuario, String motivo) {
        validarPeriodo(periodo);
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("Indique el motivo y quién autoriza la reapertura (contador y gerencia).");
        }
        PlanillaCabecera p = planillaRepository.findByPeriodo(periodo)
                .orElseThrow(() -> new RuntimeException("La planilla " + periodo + " no existe."));
        if (!PlanillaCabecera.CERRADA.equals(p.getEstado())) {
            throw new RuntimeException("La planilla " + periodo + " no está cerrada.");
        }
        p.setEstado(PlanillaCabecera.BORRADOR);
        p.setReabiertaEn(java.time.LocalDateTime.now());
        p.setReabiertaPor(usuario);
        p.setMotivoReapertura(motivo.trim());
        PlanillaCabecera guardada = planillaRepository.save(p);
        notificador.publicar("planillas", "REABIERTA", guardada.getIdPlanilla(), guardada);
        return guardada;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoletaGeneradaDTO> listarBoletas(String periodo) {
        validarPeriodo(periodo);
        return boletaRepository.findByPlanilla_Periodo(periodo).stream()
                .map(this::convertirADTO)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private void agregar(List<BoletaDetalle> lineas, String tipo, String codigo, String descripcion, BigDecimal monto) {
        if (monto == null || monto.signum() == 0) return; // no ensuciar la boleta con líneas en cero
        BoletaDetalle d = new BoletaDetalle();
        d.setTipo(tipo);
        d.setCodigo(codigo);
        d.setDescripcion(descripcion);
        d.setMonto(monto.setScale(2, RoundingMode.HALF_UP));
        lineas.add(d);
    }

    private BigDecimal suma(List<BoletaDetalle> lineas, String tipo) {
        return lineas.stream()
                .filter(l -> tipo.equals(l.getTipo()))
                .map(BoletaDetalle::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validarPeriodo(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new RuntimeException("El periodo debe tener el formato AAAA-MM (Ej: 2026-10).");
        }
    }

    private BoletaGeneradaDTO convertirADTO(BoletaPago boleta) {
        Empleado empleado = boleta.getEmpleado();
        BoletaGeneradaDTO dto = new BoletaGeneradaDTO();
        dto.setIdBoleta(boleta.getIdBoleta());
        dto.setIdEmpleado(empleado.getIdEmpleado());
        dto.setPeriodo(boleta.getPlanilla().getPeriodo());
        dto.setNombresEmpleado(empleado.getNombres() + " " + empleado.getApellidos());
        dto.setDni(empleado.getDni());
        dto.setTipoDocumento(empleado.getTipoDocumento() != null ? empleado.getTipoDocumento() : "DNI");
        dto.setRegimen(empleado.getRegimenLaboral().getNombre());
        dto.setDiasTrabajados(boleta.getDiasTrabajados());
        dto.setTotalIngresos(boleta.getTotalIngresos());
        dto.setTotalDescuentos(boleta.getTotalDescuentos());
        dto.setTotalNeto(boleta.getTotalNeto());
        dto.setRenta5taRetenida(boleta.getRenta5taRetenida());
        dto.setEssaludEmpleador(boleta.getEssaludEmpleador());
        dto.setDepositoCts(boleta.getDepositoCts());
        dto.setDetalle(boleta.getDetalle().stream()
                .map(d -> new LineaBoletaDTO(d.getTipo(), d.getCodigo(), d.getDescripcion(), d.getMonto()))
                .toList());
        return dto;
    }
}
