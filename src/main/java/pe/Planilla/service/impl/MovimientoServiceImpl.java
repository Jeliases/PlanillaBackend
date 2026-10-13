package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.HorasExtrasRequestDTO;
import pe.Planilla.model.dto.MovimientoRequestDTO;
import pe.Planilla.model.dto.VacacionRequestDTO;
import pe.Planilla.model.entity.ConceptoRemunerativo;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.MovimientoPlanilla;
import pe.Planilla.repository.ConceptoRemunerativoRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.MovimientoPlanillaRepository;
import pe.Planilla.service.MovimientoService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class MovimientoServiceImpl implements MovimientoService {

    private final MovimientoPlanillaRepository movimientoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ConceptoRemunerativoRepository conceptoRepository;

    @Override
    public MovimientoPlanilla registrarBonoOComision(MovimientoRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        ConceptoRemunerativo concepto = conceptoRepository.findByCodigo(dto.getCodigoConcepto())
                .orElseThrow(() -> new RuntimeException("Concepto no encontrado."));

        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleado(empleado);
        movimiento.setConcepto(concepto);
        movimiento.setPeriodo(dto.getPeriodo());
        movimiento.setMonto(dto.getMonto());

        return movimientoRepository.save(movimiento);
    }

    @Override
    public MovimientoPlanilla calcularYRegistrarHorasExtras(HorasExtrasRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        // Buscamos el concepto de Horas Extras (código '0201' que insertamos en el SQL)
        ConceptoRemunerativo conceptoHE = conceptoRepository.findByCodigo("0201")
                .orElseThrow(() -> new RuntimeException("El concepto de Horas Extras (0201) no existe."));

        // 1. Calcular el valor de 1 hora normal de trabajo (con 4 decimales para no perder precisión)
        BigDecimal valorDia = empleado.getSueldoBase().divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
        BigDecimal valorHoraNormal = valorDia.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP);

        // 2. Calcular el valor con recargo de Ley (25% y 35%)
        BigDecimal valorHora25 = valorHoraNormal.multiply(new BigDecimal("1.25"));
        BigDecimal valorHora35 = valorHoraNormal.multiply(new BigDecimal("1.35"));

        // 3. Multiplicar por la cantidad de horas realizadas
        BigDecimal total25 = valorHora25.multiply(new BigDecimal(dto.getHorasAl25()));
        BigDecimal total35 = valorHora35.multiply(new BigDecimal(dto.getHorasAl35()));

        // 4. Sumar todo y redondear a 2 decimales para la boleta
        BigDecimal montoTotalHorasExtras = total25.add(total35).setScale(2, RoundingMode.HALF_UP);

        // 5. Registrar el movimiento
        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleado(empleado);
        movimiento.setConcepto(conceptoHE);
        movimiento.setPeriodo(dto.getPeriodo());
        movimiento.setMonto(montoTotalHorasExtras);

        return movimientoRepository.save(movimiento);
    }

    @Override
    public MovimientoPlanilla registrarVacaciones(VacacionRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        // El código SUNAT oficial para Remuneración Vacacional es 0118.
        // Si no lo tienes en tu BD, debes insertarlo mediante el POST /api/conceptos
        ConceptoRemunerativo conceptoVacacion = conceptoRepository.findByCodigo("0118")
                .orElseThrow(() -> new RuntimeException("El concepto de Vacaciones (0118) no existe en la BD."));

        // 1. Validar límite de días según el Régimen Laboral del empleado
        int limiteDias = empleado.getRegimenLaboral().getDiasVacaciones();
        if (dto.getDiasGozados() > limiteDias) {
            throw new RuntimeException("Error: El régimen " + empleado.getRegimenLaboral().getNombre() +
                    " solo permite hasta " + limiteDias + " días de vacaciones.");
        }

        // 2. Matemática estricta: (Sueldo / 30) * diasGozados
        BigDecimal valorDia = empleado.getSueldoBase().divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
        BigDecimal montoVacaciones = valorDia.multiply(new BigDecimal(dto.getDiasGozados())).setScale(2, RoundingMode.HALF_UP);

        // 3. Registrar el movimiento
        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleado(empleado);
        movimiento.setConcepto(conceptoVacacion);
        movimiento.setPeriodo(dto.getPeriodo());
        movimiento.setMonto(montoVacaciones);

        return movimientoRepository.save(movimiento);
    }
}