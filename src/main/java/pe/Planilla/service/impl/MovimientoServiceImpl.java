package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.model.dto.HorasExtrasRequestDTO;
import pe.Planilla.model.dto.MovimientoRequestDTO;
import pe.Planilla.model.dto.MovimientoResponseDTO;
import pe.Planilla.model.dto.VacacionRequestDTO;
import pe.Planilla.model.entity.ConceptoRemunerativo;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.MovimientoPlanilla;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.ConceptoRemunerativoRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.MovimientoPlanillaRepository;
import pe.Planilla.service.MovimientoService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoServiceImpl implements MovimientoService {

    private final MovimientoPlanillaRepository movimientoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ConceptoRemunerativoRepository conceptoRepository;
    private final NotificadorTiempoReal notificador;

    @Override
    public MovimientoResponseDTO registrarBonoOComision(MovimientoRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        ConceptoRemunerativo concepto = conceptoRepository.findByCodigo(dto.getCodigoConcepto())
                .orElseThrow(() -> new RuntimeException("Concepto no encontrado."));
        validarPeriodo(dto.getPeriodo());
        if (dto.getMonto() == null || dto.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El monto debe ser mayor a cero.");
        }

        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleado(empleado);
        movimiento.setConcepto(concepto);
        movimiento.setPeriodo(dto.getPeriodo());
        movimiento.setMonto(dto.getMonto());

        return guardarYNotificar(movimiento);
    }

    @Override
    @Transactional
    public List<MovimientoResponseDTO> calcularYRegistrarHorasExtras(HorasExtrasRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        validarPeriodo(dto.getPeriodo());
        if (dto.getHorasAl25() < 0 || dto.getHorasAl35() < 0 || (dto.getHorasAl25() + dto.getHorasAl35()) == 0) {
            throw new RuntimeException("Indique al menos una hora extra (las horas no pueden ser negativas).");
        }

        // Valor de 1 hora normal: sueldo / 30 días / 8 horas (4 decimales para no perder precisión)
        BigDecimal valorDia = empleado.getSueldoBase().divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
        BigDecimal valorHoraNormal = valorDia.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP);

        // Un movimiento por tasa, con el código SUNAT de cada una (los crea el DataSeeder)
        List<MovimientoResponseDTO> resultado = new ArrayList<>();
        if (dto.getHorasAl25() > 0) {
            resultado.add(registrarHorasExtrasConTasa(empleado, dto.getPeriodo(), "0105", valorHoraNormal, "1.25", dto.getHorasAl25()));
        }
        if (dto.getHorasAl35() > 0) {
            resultado.add(registrarHorasExtrasConTasa(empleado, dto.getPeriodo(), "0106", valorHoraNormal, "1.35", dto.getHorasAl35()));
        }
        return resultado;
    }

    private MovimientoResponseDTO registrarHorasExtrasConTasa(Empleado empleado, String periodo, String codigoConcepto,
                                                             BigDecimal valorHoraNormal, String recargo, int horas) {
        ConceptoRemunerativo concepto = conceptoRepository.findByCodigo(codigoConcepto)
                .orElseThrow(() -> new RuntimeException("El concepto de Horas Extras (" + codigoConcepto + ") no existe."));
        BigDecimal monto = valorHoraNormal.multiply(new BigDecimal(recargo))
                .multiply(new BigDecimal(horas)).setScale(2, RoundingMode.HALF_UP);

        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleado(empleado);
        movimiento.setConcepto(concepto);
        movimiento.setPeriodo(periodo);
        movimiento.setMonto(monto);
        movimiento.setCantidad(horas);
        return guardarYNotificar(movimiento);
    }

    @Override
    public MovimientoResponseDTO registrarVacaciones(VacacionRequestDTO dto) {
        Empleado empleado = empleadoRepository.findById(dto.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        validarPeriodo(dto.getPeriodo());
        if (dto.getDiasGozados() <= 0) {
            throw new RuntimeException("Los días de vacaciones deben ser mayores a cero.");
        }

        // El código SUNAT oficial para Remuneración Vacacional es 0118 (lo crea el DataSeeder).
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
        movimiento.setCantidad(dto.getDiasGozados()); // la planilla descuenta estos días del sueldo para no pagarlos dos veces

        return guardarYNotificar(movimiento);
    }

    @Override
    public List<MovimientoResponseDTO> listarPorPeriodo(String periodo) {
        validarPeriodo(periodo);
        return movimientoRepository.findByPeriodoOrderByIdMovimientoDesc(periodo).stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    @Transactional
    public void eliminar(Integer idMovimiento) {
        MovimientoPlanilla movimiento = movimientoRepository.findById(idMovimiento)
                .orElseThrow(() -> new RuntimeException("Movimiento no encontrado."));
        MovimientoResponseDTO dto = convertirADTO(movimiento);
        movimientoRepository.delete(movimiento);
        notificador.publicar("movimientos", "ELIMINADO", idMovimiento, dto);
    }

    private MovimientoResponseDTO guardarYNotificar(MovimientoPlanilla movimiento) {
        MovimientoResponseDTO dto = convertirADTO(movimientoRepository.save(movimiento));
        notificador.publicar("movimientos", "CREADO", dto.getIdMovimiento(), dto); // tiempo real
        return dto;
    }

    private void validarPeriodo(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new RuntimeException("El periodo debe tener el formato AAAA-MM (Ej: 2026-10).");
        }
    }

    private MovimientoResponseDTO convertirADTO(MovimientoPlanilla m) {
        MovimientoResponseDTO dto = new MovimientoResponseDTO();
        dto.setIdMovimiento(m.getIdMovimiento());
        dto.setIdEmpleado(m.getEmpleado().getIdEmpleado());
        dto.setEmpleado(m.getEmpleado().getNombres() + " " + m.getEmpleado().getApellidos());
        dto.setCodigoConcepto(m.getConcepto().getCodigo());
        dto.setConcepto(m.getConcepto().getDescripcion());
        dto.setTipo(m.getConcepto().getTipo());
        dto.setPeriodo(m.getPeriodo());
        dto.setMonto(m.getMonto());
        dto.setCantidad(m.getCantidad());
        return dto;
    }
}
