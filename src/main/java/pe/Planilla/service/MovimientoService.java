package pe.Planilla.service;

import pe.Planilla.model.dto.HorasExtrasRequestDTO;
import pe.Planilla.model.dto.MovimientoRequestDTO;
import pe.Planilla.model.dto.MovimientoResponseDTO;
import pe.Planilla.model.dto.VacacionRequestDTO;

import java.util.List;

public interface MovimientoService {
    MovimientoResponseDTO registrarBonoOComision(MovimientoRequestDTO dto);
    /** Registra un movimiento por cada tasa con horas (25% -> 0105, 35% -> 0106), como pide el PLAME. */
    List<MovimientoResponseDTO> calcularYRegistrarHorasExtras(HorasExtrasRequestDTO dto);
    MovimientoResponseDTO registrarVacaciones(VacacionRequestDTO dto);
    List<MovimientoResponseDTO> listarPorPeriodo(String periodo);
    void eliminar(Integer idMovimiento);
}
