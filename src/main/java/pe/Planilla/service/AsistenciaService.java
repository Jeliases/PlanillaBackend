package pe.Planilla.service;

import pe.Planilla.model.dto.AsistenciaRequestDTO;
import pe.Planilla.model.dto.AsistenciaResponseDTO;
import java.util.List;

public interface AsistenciaService {
    AsistenciaResponseDTO registrarTareo(AsistenciaRequestDTO requestDTO);
    List<AsistenciaResponseDTO> buscarPorEmpleadoYMes(Integer idEmpleado, int mes, int anio);
    int calcularTotalMinutosTardanzaMes(Integer idEmpleado, int mes, int anio);
}