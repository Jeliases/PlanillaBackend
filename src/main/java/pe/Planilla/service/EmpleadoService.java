package pe.Planilla.service;

import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;

import java.util.List;

public interface EmpleadoService {
    List<EmpleadoResponseDTO> listarTodos();
    EmpleadoResponseDTO registrarEmpleado(EmpleadoRequestDTO requestDTO);
    EmpleadoResponseDTO buscarPorId(Integer id);
}