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

    /** Edita los datos de un empleado existente (solo los campos que vienen). */
    EmpleadoResponseDTO actualizar(Integer id, EmpleadoRequestDTO dto);

    /** Registra el cese: desde el mes siguiente deja de entrar en la planilla. */
    EmpleadoResponseDTO cesar(Integer id, java.time.LocalDate fechaCese, String motivo);

    /** Vuelve a activar a un cesado (por ejemplo, un cese registrado por error). */
    EmpleadoResponseDTO reactivar(Integer id);
}
