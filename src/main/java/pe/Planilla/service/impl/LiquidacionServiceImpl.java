package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.LiquidacionRequestDTO;
import pe.Planilla.model.dto.LiquidacionResponseDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.service.LiquidacionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class LiquidacionServiceImpl implements LiquidacionService {

    private final EmpleadoRepository empleadoRepository;

    @Override
    public LiquidacionResponseDTO calcularLiquidacion(LiquidacionRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        // 1. Validar que la fecha de cese sea posterior a la fecha de ingreso
        if (dto.getFechaCese().isBefore(empleado.getFechaIngreso())) {
            throw new RuntimeException("La fecha de cese no puede ser anterior a la fecha de ingreso.");
        }

        // 2. Extraer el factor de cálculo según su régimen (General=1.0, Mype=0.5, Remype=0.0)
        BigDecimal factorRegimen = empleado.getRegimenLaboral().getFactorLiquidacion();

        // 3. Calcular tiempo de servicio usando Period (Java Time)
        Period periodoTrabajado = Period.between(empleado.getFechaIngreso(), dto.getFechaCese());
        int mesesTrabajados = periodoTrabajado.getYears() * 12 + periodoTrabajado.getMonths();
        int diasTrabajados = periodoTrabajado.getDays();

        String textoTiempoServicio = periodoTrabajado.getYears() + " años, " +
                periodoTrabajado.getMonths() + " meses, " +
                diasTrabajados + " días";

        // === FÓRMULAS EXACTAS DE LEY PERUANA ===

        // A. VACACIONES TRUNCAS
        // Fórmula: (Sueldo Básico / 12 * meses) + (Sueldo Básico / 360 * días) * Factor Régimen
        BigDecimal sueldoBasico = empleado.getSueldoBase();

        BigDecimal porMesVac = sueldoBasico.divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(mesesTrabajados));
        BigDecimal porDiaVac = sueldoBasico.divide(new BigDecimal("360"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(diasTrabajados));

        BigDecimal vacTruncas = porMesVac.add(porDiaVac).multiply(factorRegimen).setScale(2, RoundingMode.HALF_UP);

        // Si el factor es 0.0 (Remype), el sistema arrojará 0 automáticamente en CTS y Grati.

        // B. GRATIFICACIÓN TRUNCA
        // Solo se pagan meses completos calendarios. Los días no se cuentan para la gratificación.
        // Fórmula: (Sueldo Básico / 6) * Meses * Factor Régimen (1.0 o 0.5)
        BigDecimal gratiTrunca = BigDecimal.ZERO;
        if (mesesTrabajados > 0) {
            BigDecimal unSextoGrati = sueldoBasico.divide(new BigDecimal("6"), 4, RoundingMode.HALF_UP);
            gratiTrunca = unSextoGrati.multiply(new BigDecimal(mesesTrabajados)).multiply(factorRegimen).setScale(2, RoundingMode.HALF_UP);
        }

        // C. CTS TRUNCA
        // Para la CTS, el sueldo computable incluye 1/6 de la gratificación.
        // Fórmula: (Sueldo Computable / 12 * meses) + (Sueldo Computable / 360 * días) * Factor Régimen
        BigDecimal unSextoGratiCTS = sueldoBasico.divide(new BigDecimal("6"), 4, RoundingMode.HALF_UP);
        BigDecimal sueldoComputableCTS = sueldoBasico.add(unSextoGratiCTS);

        BigDecimal porMesCTS = sueldoComputableCTS.divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(mesesTrabajados));
        BigDecimal porDiaCTS = sueldoComputableCTS.divide(new BigDecimal("360"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(diasTrabajados));

        BigDecimal ctsTrunca = porMesCTS.add(porDiaCTS).multiply(factorRegimen).setScale(2, RoundingMode.HALF_UP);

        // 4. Armar el DTO de respuesta
        LiquidacionResponseDTO response = new LiquidacionResponseDTO();
        response.setNombresEmpleado(empleado.getNombres() + " " + empleado.getApellidos());
        response.setRegimen(empleado.getRegimenLaboral().getNombre());
        response.setTiempoServicio(textoTiempoServicio);
        response.setMontoVacacionesTruncas(vacTruncas);
        response.setMontoGratificacionTrunca(gratiTrunca);
        response.setMontoCtsTrunca(ctsTrunca);
        response.setTotalLiquidacion(vacTruncas.add(gratiTrunca).add(ctsTrunca));

        return response;
    }
}