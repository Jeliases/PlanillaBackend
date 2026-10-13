package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.RegimenLaboralRepository;
import pe.Planilla.service.EmpleadoService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpleadoServiceImpl implements EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;

    @Override
    public List<EmpleadoResponseDTO> listarTodos() {
        return empleadoRepository.findAll().stream()
                .map(this::convertirAResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EmpleadoResponseDTO registrarEmpleado(EmpleadoRequestDTO requestDTO) {
        if (empleadoRepository.findByDni(requestDTO.getDni()).isPresent()) {
            throw new RuntimeException("El empleado con DNI " + requestDTO.getDni() + " ya está registrado.");
        }

        RegimenLaboral regimen = regimenLaboralRepository.findById(requestDTO.getIdRegimen())
                .orElseThrow(() -> new RuntimeException("Régimen Laboral no encontrado."));

        Empleado empleado = new Empleado();
        empleado.setNombres(requestDTO.getNombres());
        empleado.setApellidos(requestDTO.getApellidos());
        empleado.setDni(requestDTO.getDni());
        empleado.setFechaIngreso(requestDTO.getFechaIngreso());
        empleado.setSueldoBase(requestDTO.getSueldoBase());
        empleado.setCuentaBancaria(requestDTO.getCuentaBancaria());
        empleado.setRegimenLaboral(regimen);
        empleado.setEstado("ACTIVO");

        Empleado empleadoGuardado = empleadoRepository.save(empleado);
        return convertirAResponseDTO(empleadoGuardado);
    }

    @Override
    public EmpleadoResponseDTO buscarPorId(Integer id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        return convertirAResponseDTO(empleado);
    }

    // Método auxiliar privado
    private EmpleadoResponseDTO convertirAResponseDTO(Empleado empleado) {
        EmpleadoResponseDTO dto = new EmpleadoResponseDTO();
        dto.setIdEmpleado(empleado.getIdEmpleado());
        dto.setNombresCompletos(empleado.getNombres() + " " + empleado.getApellidos());
        dto.setDni(empleado.getDni());
        dto.setFechaIngreso(empleado.getFechaIngreso());
        dto.setSueldoBase(empleado.getSueldoBase());
        dto.setRegimenLaboral(empleado.getRegimenLaboral().getNombre());
        dto.setEstado(empleado.getEstado());
        return dto;
    }
}