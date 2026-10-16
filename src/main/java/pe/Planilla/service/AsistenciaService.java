package pe.Planilla.service;

import pe.Planilla.model.dto.AsistenciaRequestDTO;
import pe.Planilla.model.dto.AsistenciaResponseDTO;
import java.util.List;

public interface AsistenciaService {
    /** Registra el tareo del día. Si el empleado ya tenía registro esa fecha, lo corrige (no duplica). */
    AsistenciaResponseDTO registrarTareo(AsistenciaRequestDTO requestDTO);
    List<AsistenciaResponseDTO> buscarPorEmpleadoYMes(Integer idEmpleado, int mes, int anio);
    /** Todas las asistencias de todos los empleados en un periodo AAAA-MM. */
    List<AsistenciaResponseDTO> listarPorPeriodo(String periodo);
    int calcularTotalMinutosTardanzaMes(Integer idEmpleado, int mes, int anio);
}
