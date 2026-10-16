package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.AsistenciaRequestDTO;
import pe.Planilla.model.dto.AsistenciaResponseDTO;
import pe.Planilla.model.entity.AsistenciaDiaria;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.AsistenciaDiariaRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.service.AsistenciaService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsistenciaServiceImpl implements AsistenciaService {

    private static final Set<String> ESTADOS_VALIDOS =
            Set.of("ASISTIO", "FALTA", "TARDANZA", "PERMISO", "DESCANSO_MEDICO");

    private final AsistenciaDiariaRepository asistenciaRepository;
    private final EmpleadoRepository empleadoRepository; // Necesario para buscar al empleado por ID
    private final NotificadorTiempoReal notificador;

    @Override
    public AsistenciaResponseDTO registrarTareo(AsistenciaRequestDTO requestDTO) {
        Empleado empleado = empleadoRepository.findById(requestDTO.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));

        if (requestDTO.getFecha() == null) {
            throw new RuntimeException("La fecha es obligatoria.");
        }
        if (requestDTO.getEstado() == null || !ESTADOS_VALIDOS.contains(requestDTO.getEstado().toUpperCase())) {
            throw new RuntimeException("Estado inválido. Use: " + String.join(", ", ESTADOS_VALIDOS) + ".");
        }

        // Un solo registro por empleado y día: si ya existe se corrige (evita doble conteo de tardanzas)
        AsistenciaDiaria asistencia = asistenciaRepository
                .findFirstByEmpleado_IdEmpleadoAndFecha(empleado.getIdEmpleado(), requestDTO.getFecha())
                .orElseGet(AsistenciaDiaria::new);
        boolean esNueva = asistencia.getIdAsistencia() == null;

        asistencia.setEmpleado(empleado);
        asistencia.setFecha(requestDTO.getFecha());
        asistencia.setHoraIngreso(requestDTO.getHoraIngreso());
        asistencia.setHoraSalida(requestDTO.getHoraSalida());
        asistencia.setEstado(requestDTO.getEstado().toUpperCase()); // Normalizamos a mayúsculas
        asistencia.setMinutosTardanza(requestDTO.getMinutosTardanza() != null ? requestDTO.getMinutosTardanza() : 0);

        AsistenciaDiaria guardada = asistenciaRepository.save(asistencia);
        AsistenciaResponseDTO dto = convertirAResponseDTO(guardada);
        notificador.publicar("asistencias", esNueva ? "CREADO" : "ACTUALIZADO", dto.getIdAsistencia(), dto); // tiempo real
        return dto;
    }

    @Override
    public List<AsistenciaResponseDTO> buscarPorEmpleadoYMes(Integer idEmpleado, int mes, int anio) {
        return asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio)
                .stream().map(this::convertirAResponseDTO).collect(Collectors.toList());
    }

    @Override
    public List<AsistenciaResponseDTO> listarPorPeriodo(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new RuntimeException("El periodo debe tener el formato AAAA-MM (Ej: 2026-10).");
        }
        YearMonth ym = YearMonth.parse(periodo);
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();
        return asistenciaRepository.findByFechaBetweenOrderByFechaDescIdAsistenciaDesc(desde, hasta)
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
        dto.setIdEmpleado(asistencia.getEmpleado().getIdEmpleado());
        dto.setNombreEmpleado(asistencia.getEmpleado().getNombres() + " " + asistencia.getEmpleado().getApellidos());
        dto.setFecha(asistencia.getFecha());
        dto.setHoraIngreso(asistencia.getHoraIngreso());
        dto.setHoraSalida(asistencia.getHoraSalida());
        dto.setEstado(asistencia.getEstado());
        dto.setMinutosTardanza(asistencia.getMinutosTardanza());
        return dto;
    }
}
