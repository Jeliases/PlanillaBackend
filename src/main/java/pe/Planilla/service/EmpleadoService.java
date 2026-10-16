package pe.Planilla.service;

import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.model.entity.Empleado;

import java.util.List;

public interface EmpleadoService {
    List<EmpleadoResponseDTO> listarTodos();
    EmpleadoResponseDTO registrarEmpleado(EmpleadoRequestDTO requestDTO);
    EmpleadoResponseDTO buscarPorId(Integer id);

    /**
     * Valida todos los datos y arma el empleado SIN guardarlo (lo usa la carga masiva para revisar cada fila).
     * Lanza RuntimeException con un mensaje en español si algo está mal.
     */
    Empleado construirEmpleado(EmpleadoRequestDTO requestDTO);

    /** Guarda varios empleados en una sola transacción y avisa a las pantallas en vivo. */
    List<EmpleadoResponseDTO> guardarLote(List<Empleado> empleados);

    EmpleadoResponseDTO aDTO(Empleado empleado);
}
