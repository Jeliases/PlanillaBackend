package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.entity.*;
import pe.Planilla.repository.*;
import pe.Planilla.service.AsistenciaService;
import pe.Planilla.service.PlanillaService;
import pe.Planilla.service.RentaQuintaService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanillaServiceImpl implements PlanillaService {

    private final EmpleadoRepository empleadoRepository;
    private final PlanillaCabeceraRepository planillaRepository;
    private final BoletaPagoRepository boletaRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final AsistenciaService asistenciaService;
    private final RentaQuintaService rentaQuintaService;

    // Para simplificar ahora, asumiremos que existe un Concepto con ID 1 que es "Sueldo Básico"
    private final ConceptoRemunerativoRepository conceptoRepository;

    @Override
    @Transactional // Si algo falla en el cálculo, se revierte todo (No hay boletas a medias)
    public BoletaGeneradaDTO procesarBoletaMensual(Integer idEmpleado, String periodo) {

        Empleado empleado = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        // 1. Validar o crear la Planilla Cabecera del mes (Ej: "2026-10")
        PlanillaCabecera planilla = planillaRepository.findByPeriodo(periodo)
                .orElseGet(() -> {
                    PlanillaCabecera nueva = new PlanillaCabecera();
                    nueva.setPeriodo(periodo);
                    nueva.setEstado("BORRADOR");
                    return planillaRepository.save(nueva);
                });

        // 2. Extraer el mes y año del string "2026-10" para consultar asistencias
        String[] partesPeriodo = periodo.split("-");
        int anio = Integer.parseInt(partesPeriodo[0]);
        int mes = Integer.parseInt(partesPeriodo[1]);

        // 3. MATEMÁTICA ESTRICTA (REGLA DE LOS 30 DÍAS)
        BigDecimal diasMesComercial = new BigDecimal("30");

        // Aquí deberíamos contar los días asistidos reales. Por ahora asumiremos mes completo (30 días)
        BigDecimal diasTrabajados = new BigDecimal("30");

        // Fórmula: (Sueldo / 30) * Días Trabajados. (HALF_UP redondea céntimos comerciales: 0.5 sube a 1)
        BigDecimal valorDia = empleado.getSueldoBase().divide(diasMesComercial, 2, RoundingMode.HALF_UP);
        BigDecimal ingresosBrutos = valorDia.multiply(diasTrabajados).setScale(2, RoundingMode.HALF_UP);

        // --- SUMAR MOVIMIENTOS ADICIONALES (Horas Extras, Bonos, Comisiones, Vacaciones) ---
        // Buscamos si este empleado tiene horas extras o bonos registrados en este mes
        List<MovimientoPlanilla> movimientos = movimientoRepository.findByEmpleado_IdEmpleadoAndPeriodo(idEmpleado, periodo);

        for (MovimientoPlanilla mov : movimientos) {
            if ("INGRESO".equals(mov.getConcepto().getTipo())) {
                ingresosBrutos = ingresosBrutos.add(mov.getMonto());
            }
        }
        // ------------------------------------------------------------------------------

        // 4. CÁLCULO DE DESCUENTOS (Tardanzas y Pensiones)
        BigDecimal totalDescuentos = BigDecimal.ZERO;

        // Descuento por Tardanza (Se calcula valor por minuto base, sin incluir horas extras)
        int minutosTardanza = asistenciaService.calcularTotalMinutosTardanzaMes(idEmpleado, mes, anio);
        if (minutosTardanza > 0) {
            // (Valor Día / 8 horas / 60 minutos) = Valor por minuto
            BigDecimal valorHora = valorDia.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP);
            BigDecimal valorMinuto = valorHora.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);

            BigDecimal descuentoTardanza = valorMinuto.multiply(new BigDecimal(minutosTardanza)).setScale(2, RoundingMode.HALF_UP);
            totalDescuentos = totalDescuentos.add(descuentoTardanza);
        }

        // Si es Régimen General o Mype y tiene ONP, descontamos 13% sobre el TOTAL de ingresos (sueldo + extras)
        if (!"Cuarta Categoría (RxH)".equals(empleado.getRegimenLaboral().getNombre())) {
            // Asumiremos un 13% de ONP temporalmente hasta enganchar la tabla de pensiones al 100%
            BigDecimal descuentoPension = ingresosBrutos.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);
            totalDescuentos = totalDescuentos.add(descuentoPension);
        }

        // --- NUEVO: CÁLCULO DE RENTA DE 5TA CATEGORÍA ---
        // Solo aplica para el Régimen General y Mype, no para RxH (que es 4ta categoría)
        BigDecimal retencionRenta5ta = BigDecimal.ZERO;

        if (!"Cuarta Categoría (RxH)".equals(empleado.getRegimenLaboral().getNombre())) {
            retencionRenta5ta = rentaQuintaService.calcularRetencionMensual(ingresosBrutos);
            if (retencionRenta5ta.compareTo(BigDecimal.ZERO) > 0) {
                totalDescuentos = totalDescuentos.add(retencionRenta5ta);
            }
        }
        // ------------------------------------------------

        // 5. TOTAL NETO (Lo que va al bolsillo)
        BigDecimal totalNeto = ingresosBrutos.subtract(totalDescuentos);

        // 6. GUARDAR LA BOLETA FINAL
        BoletaPago boleta = new BoletaPago();
        boleta.setPlanilla(planilla);
        boleta.setEmpleado(empleado);
        boleta.setTotalIngresos(ingresosBrutos);
        boleta.setTotalDescuentos(totalDescuentos);
        boleta.setTotalNeto(totalNeto);
        boleta.setRenta5taRetenida(retencionRenta5ta); // <-- AHORA GUARDAMOS EL MONTO REAL DE 5TA

        boletaRepository.save(boleta);

        // 7. PREPARAR EL DTO DE RESPUESTA
        BoletaGeneradaDTO dto = new BoletaGeneradaDTO();
        dto.setPeriodo(periodo);
        dto.setNombresEmpleado(empleado.getNombres() + " " + empleado.getApellidos());
        dto.setDni(empleado.getDni());
        dto.setRegimen(empleado.getRegimenLaboral().getNombre());
        dto.setTotalIngresos(ingresosBrutos);
        dto.setTotalDescuentos(totalDescuentos);
        dto.setTotalNeto(totalNeto);

        return dto;
    }
}