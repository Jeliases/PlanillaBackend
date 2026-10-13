package pe.Planilla.service;

import pe.Planilla.model.dto.HorasExtrasRequestDTO;
import pe.Planilla.model.dto.MovimientoRequestDTO;
import pe.Planilla.model.dto.VacacionRequestDTO;
import pe.Planilla.model.entity.MovimientoPlanilla;

public interface MovimientoService {
    MovimientoPlanilla registrarBonoOComision(MovimientoRequestDTO dto);
    MovimientoPlanilla calcularYRegistrarHorasExtras(HorasExtrasRequestDTO dto);
    MovimientoPlanilla registrarVacaciones(VacacionRequestDTO dto);
}

