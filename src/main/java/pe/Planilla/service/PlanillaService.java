package pe.Planilla.service;

import pe.Planilla.model.dto.BoletaGeneradaDTO;

import java.util.List;

public interface PlanillaService {
    /** Calcula (o recalcula) la boleta de un empleado en el periodo. Si ya existía, la actualiza: no duplica. */
    BoletaGeneradaDTO procesarBoletaMensual(Integer idEmpleado, String periodo);

    /** Procesa a todos los empleados ACTIVOS del periodo en una sola transacción (todo o nada). */
    List<BoletaGeneradaDTO> procesarPlanilla(String periodo);

    List<BoletaGeneradaDTO> listarBoletas(String periodo);
}
