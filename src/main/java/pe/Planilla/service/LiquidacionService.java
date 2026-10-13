package pe.Planilla.service;

import pe.Planilla.model.dto.LiquidacionRequestDTO;
import pe.Planilla.model.dto.LiquidacionResponseDTO;

public interface LiquidacionService {
    LiquidacionResponseDTO calcularLiquidacion(LiquidacionRequestDTO dto);
}