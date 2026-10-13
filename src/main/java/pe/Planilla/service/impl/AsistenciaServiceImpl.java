package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.AsistenciaRequestDTO;
import pe.Planilla.model.dto.AsistenciaResponseDTO;
import pe.Planilla.model.entity.AsistenciaDiaria;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.repository.AsistenciaDiariaRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.service.AsistenciaService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsistenciaServiceImpl implements AsistenciaService {

    private final AsistenciaDiariaRepository asistenciaRepository;
    private final EmpleadoRepository empleadoRepository; // Necesario para buscar al empleado por ID

    @Override
    public AsistenciaResponseDTO registrarTareo(AsistenciaRequestDTO requestDTO) {
        Empleado empleado = empleadoRepository.findById(requestDTO.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        AsistenciaDiaria asistencia = new AsistenciaDiaria();
        asistencia.setEmpleado(empleado);
        asistencia.setFecha(requestDTO.getFecha());
        asistencia.setHoraIngreso(requestDTO.getHoraIngreso());
        asistencia.setHoraSalida(requestDTO.getHoraSalida());
        asistencia.setEstado(requestDTO.getEstado().toUpperCase()); // Normalizamos a mayúsculas
        asistencia.setMinutosTardanza(requestDTO.getMinutosTardanza() != null ? requestDTO.getMinutosTardanza() : 0);

        AsistenciaDiaria guardada = asistenciaRepository.save(asistencia);
        return convertirAResponseDTO(guardada);
    }

    @Override
    public List<AsistenciaResponseDTO> buscarPorEmpleadoYMes(Integer idEmpleado, int mes, int anio) {
        return asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio)
                .stream().map(this::convertirAResponseDTO).collect(Collectors.toList());
    }

    @Override
    public int calcularTotalMinutosTardanzaMes(Integer idEmpleado, int mes, int anio) {
        List<AsistenciaDiaria> asistencias = asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio);
        return asistencias.stream()
                .filter(a -> "TARDANZA".equals(a.getEstado()) || "ASISTIO".equals(a.getEstado()))
                .mapToInt(a -> a.getMinutosTardanza() != null ? a.getMinutosTardanza() : 0)
                .sum();
    }

    // Convertidor a DTO
    private AsistenciaResponseDTO convertirAResponseDTO(AsistenciaDiaria asistencia) {
        AsistenciaResponseDTO dto = new AsistenciaResponseDTO();
        dto.setIdAsistencia(asistencia.getIdAsistencia());
        dto.setNombreEmpleado(asistencia.getEmpleado().getNombres() + " " + asistencia.getEmpleado().getApellidos());
        dto.setFecha(asistencia.getFecha());
        dto.setHoraIngreso(asistencia.getHoraIngreso());
        dto.setHoraSalida(asistencia.getHoraSalida());
        dto.setEstado(asistencia.getEstado());
        dto.setMinutosTardanza(asistencia.getMinutosTardanza());
        return dto;
    }
}