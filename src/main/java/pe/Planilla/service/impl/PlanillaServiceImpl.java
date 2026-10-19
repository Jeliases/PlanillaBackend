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
        BigDecimal sueldoPagado = calculadora.prorratear(empleado.getSueldoBase(), diasPagados);
        agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.BASICO, "Remuneración básica (" + diasPagados + " días)", sueldoPagado);

        BigDecimal asignacionMensual = BigDecimal.ZERO;
        BigDecimal asignacionPagada = BigDecimal.ZERO;
        // Asignación familiar: el empleado la tiene marcada Y su régimen la paga (solo el Régimen General)
        if (dependiente && Boolean.TRUE.equals(empleado.getAsignacionFamiliar())
                && Boolean.TRUE.equals(regimen.getAsignacionFamiliar())) {
            asignacionMensual = calculadora.asignacionFamiliarMensual();
            asignacionPagada = calculadora.prorratear(asignacionMensual, diasPagados);
            agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.ASIGNACION_FAMILIAR, "Asignación familiar", asignacionPagada);
        }

        BigDecimal ingresosMovimientos = BigDecimal.ZERO;
        for (MovimientoPlanilla mov : movimientos) {
            ConceptoRemunerativo c = mov.getConcepto();
            if ("INGRESO".equals(c.getTipo())) {
                agregar(lineas, BoletaDetalle.INGRESO, c.getCodigo(), c.getDescripcion(), mov.getMonto());
                ingresosMovimientos = ingresosMovimientos.add(mov.getMonto());
            } else if ("DESCUENTO".equals(c.getTipo())) {
                agregar(lineas, BoletaDetalle.DESCUENTO, c.getCodigo(), c.getDescripcion(), mov.getMonto());
            }
        }

        // Horas extras autorizadas en la asistencia (las 2 primeras de cada día al 25%, el resto al 35%), sobre el básico
        int[] he = asistenciaService.horasExtraAutorizadasDelMes(idEmpleado, mes, anio);
        if (he[0] + he[1] > 0) {
            BigDecimal valorHora = empleado.getSueldoBase().divide(new BigDecimal("240"), 4, RoundingMode.HALF_UP); // /30 días /8 horas
            if (he[0] > 0) {
                BigDecimal m25 = valorHora.multiply(new BigDecimal("1.25")).multiply(new BigDecimal(he[0])).setScale(2, RoundingMode.HALF_UP);
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.HORAS_EXTRAS_25, "Horas extras 25% (" + he[0] + " h, asistencia)", m25);
                ingresosMovimientos = ingresosMovimientos.add(m25);
            }
            if (he[1] > 0) {
                BigDecimal m35 = valorHora.multiply(new BigDecimal("1.35")).multiply(new BigDecimal(he[1])).setScale(2, RoundingMode.HALF_UP);
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.HORAS_EXTRAS_35, "Horas extras 35% (" + he[1] + " h, asistencia)", m35);
                ingresosMovimientos = ingresosMovimientos.add(m35);
            }
        }

        // Remuneración sobre la que se aporta a pensiones y EsSalud (la gratificación NO entra: es inafecta)
        BigDecimal baseAfecta = sueldoPagado.add(asignacionPagada).add(ingresosMovimientos);

        // 4. GRATIFICACIÓN (julio y diciembre) + bonificación extraordinaria
        BigDecimal fijaMensual = empleado.getSueldoBase().add(asignacionMensual);
        if (dependiente && (mes == 7 || mes == 12)) {
            int meses = calculadora.mesesComputablesGratificacion(empleado.getFechaIngreso(), ym);
            long faltasSemestre = asistenciaRepository.countByEmpleado_IdEmpleadoAndEstadoAndJustificadaFalseAndFechaBetween(idEmpleado, "FALTA",
                    calculadora.inicioSemestreGratificacion(ym), calculadora.finSemestreGratificacion(ym));
            Gratificacion g = calculadora.gratificacion(fijaMensual, factor, meses, faltasSemestre);
            if (g.neta().signum() > 0) {
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.GRATIFICACION,
                        "Gratificación " + (mes == 7 ? "Fiestas Patrias" : "Navidad") + " (" + g.mesesComputables() + "/6 meses)", g.neta());
                agregar(lineas, BoletaDetalle.INGRESO, CodigosPlame.BONIF_EXTRAORDINARIA,
                        "Bonificación extraordinaria (" + props.getBonificacionExtraordinariaPct().movePointRight(2).stripTrailingZeros().toPlainString() + "%)",
                        g.bonificacionExtraordinaria());
            }
        }

        // 5. DESCUENTOS: tardanzas, pensión (ONP/AFP) y Renta de 5ta
        // Tardanzas: tabla del comunicado de la empresa (monto fijo por tramo o medio día) y un día adicional
        // al llegar a N tardanzas en el mes. Las tardanzas justificadas con documentación no se descuentan.
        List<Integer> tardanzas = asistenciaService.minutosTardanzaInjustificadaDelMes(idEmpleado, mes, anio);
        DescuentoTardanzas dt = calculadoraAsistencia.descuentoTardanzas(
                tardanzas, empleado.getSueldoBase(), reglaTardanzaRepository.findAllByOrderByDesdeMinutoAsc());
        agregar(lineas, BoletaDetalle.DESCUENTO, null, "Descuento por tardanzas (" + dt.cantidad() + ")", dt.montoTramos());
        agregar(lineas, BoletaDetalle.DESCUENTO, null,
                "Descuento de 1 día adicional (" + dt.cantidad() + " tardanzas en el mes)", dt.montoDiaAdicional());

        BigDecimal renta5ta = BigDecimal.ZERO;
        BigDecimal essaludEmpleador = BigDecimal.ZERO;
        BigDecimal depositoCts = BigDecimal.ZERO;

        if (dependiente) {
            for (LineaPension l : calculadora.pension(empleado.getSistemaPensionario(), empleado.getAfp(),
                    empleado.getTipoComisionAfp(), baseAfecta)) {
                agregar(lineas, BoletaDetalle.DESCUENTO, l.codigo(), l.descripcion(), l.monto());
            }

            renta5ta = calcularRenta5ta(empleado, ym, factor, fijaMensual, suma(lineas, BoletaDetalle.INGRESO));
            agregar(lineas, BoletaDetalle.DESCUENTO, CodigosPlame.RENTA_5TA, "Renta de 5ta categoría", renta5ta);

            // 6. Costos del empleador (informativos, no restan al neto)
            essaludEmpleador = calculadora.essaludEmpleador(baseAfecta);
            agregar(lineas, BoletaDetalle.INFORMATIVO, CodigosPlame.ESSALUD,
                    "EsSalud empleador (" + props.getEssaludPct().movePointRight(2).stripTrailingZeros().toPlainString() + "%)", essaludEmpleador);

            if ((mes == 5 || mes == 11) && factor.signum() > 0) {
                depositoCts = calcularCts(empleado, ym, factor, fijaMensual, lineas);
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
     * Retención de 5ta: acumula lo ya percibido en el año. Si faltan boletas de meses anteriores (el sistema se empezó a
     * usar a mitad de año) se estima con la remuneración actual para no subestimar la proyección anual.
     */
    private BigDecimal calcularRenta5ta(Empleado empleado, YearMonth ym, BigDecimal factor, BigDecimal fijaMensual, BigDecimal ingresoMesActual) {
        int anio = ym.getYear();
        int mes = ym.getMonthValue();
        List<BoletaPago> delAnio = boletaRepository
                .findByPlanilla_PeriodoStartingWithAndEmpleado_IdEmpleado(anio + "-", empleado.getIdEmpleado());
        int mesCorte = rentaQuintaService.mesCorteRetenciones(mes);

        BigDecimal ingresosPrevios = BigDecimal.ZERO;
        BigDecimal retencionesPrevias = BigDecimal.ZERO;
        for (int m = 1; m < mes; m++) {
            String per = String.format("%d-%02d", anio, m);
            BoletaPago b = delAnio.stream().filter(x -> per.equals(x.getPlanilla().getPeriodo())).findFirst().orElse(null);
            if (b != null) {
                ingresosPrevios = ingresosPrevios.add(b.getTotalIngresos());
                if (m <= mesCorte && b.getRenta5taRetenida() != null) {
                    retencionesPrevias = retencionesPrevias.add(b.getRenta5taRetenida());
                }
            } else {
                ingresosPrevios = ingresosPrevios.add(estimarIngresoMes(empleado, YearMonth.of(anio, m), fijaMensual, factor));
            }
        }

        return rentaQuintaService.calcularRetencionMensual(
                new DatosRenta5ta(mes, ingresosPrevios, ingresoMesActual, fijaMensual, factor, retencionesPrevias));
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

    /** CTS de mayo/noviembre: remuneración + 1/6 de la última gratificación. Se informa, no se paga en la boleta. */
    private BigDecimal calcularCts(Empleado empleado, YearMonth ym, BigDecimal factor, BigDecimal fijaMensual, List<BoletaDetalle> lineas) {
        int mes = ym.getMonthValue();
        String periodoGrati = mes == 5 ? (ym.getYear() - 1) + "-12" : ym.getYear() + "-07";

        // 1/6 de la última gratificación pagada (equivalente a régimen completo); si no hay boleta, la teórica
        BigDecimal gratiCompleta = boletaRepository
                .findByPlanilla_PeriodoAndEmpleado_IdEmpleado(periodoGrati, empleado.getIdEmpleado())
                .flatMap(b -> b.getDetalle().stream()
                        .filter(d -> CodigosPlame.GRATIFICACION.equals(d.getCodigo()))
                        .map(BoletaDetalle::getMonto)
                        .findFirst())
                .map(monto -> monto.divide(factor, 2, RoundingMode.HALF_UP))
                .orElse(fijaMensual);
        BigDecimal sextoGrati = gratiCompleta.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP);
        BigDecimal computable = fijaMensual.add(sextoGrati);

        long faltasSemestre = asistenciaRepository.countByEmpleado_IdEmpleadoAndEstadoAndJustificadaFalseAndFechaBetween(empleado.getIdEmpleado(), "FALTA",
                calculadora.inicioSemestreCts(ym), calculadora.finSemestreCts(ym));
        Cts cts = calculadora.cts(computable, factor, empleado.getFechaIngreso(), ym, faltasSemestre);
        if (cts.monto().signum() > 0) {
            agregar(lineas, BoletaDetalle.INFORMATIVO, null,
                    "CTS a depositar (" + cts.meses() + " meses y " + cts.dias() + " días)", cts.monto());
        }
        return cts.monto();
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
