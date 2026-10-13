package pe.Planilla.service;

import pe.Planilla.model.dto.BoletaGeneradaDTO;

public interface PlanillaService {
    BoletaGeneradaDTO procesarBoletaMensual(Integer idEmpleado, String periodo);
}