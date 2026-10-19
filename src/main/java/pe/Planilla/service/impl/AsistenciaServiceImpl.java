package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        asistencia.setMarcaIngreso(requestDTO.getHoraIngreso());
        asistencia.setMarcaSalida(requestDTO.getHoraSalida());
        asistencia.setEstado(requestDTO.getEstado().toUpperCase()); // Normalizamos a mayúsculas
        asistencia.setMinutosTardanza(requestDTO.getMinutosTardanza() != null ? requestDTO.getMinutosTardanza() : 0);
        asistencia.setOrigen("MANUAL");

        AsistenciaDiaria guardada = asistenciaRepository.save(asistencia);
        AsistenciaResponseDTO dto = aDTO(guardada);
        notificador.publicar("asistencias", esNueva ? "CREADO" : "ACTUALIZADO", dto.getIdAsistencia(), dto); // tiempo real
        return dto;
    }

    @Override
    public List<AsistenciaResponseDTO> buscarPorEmpleadoYMes(Integer idEmpleado, int mes, int anio) {
        return asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio)
                .stream().map(this::aDTO).collect(Collectors.toList());
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
                .stream().map(this::aDTO).collect(Collectors.toList());
    }

    @Override
    public int calcularTotalMinutosTardanzaMes(Integer idEmpleado, int mes, int anio) {
        List<AsistenciaDiaria> asistencias = asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio);
        return asistencias.stream()
                .filter(a -> "TARDANZA".equals(a.getEstado()) || "ASISTIO".equals(a.getEstado()))
                .mapToInt(a -> a.getMinutosTardanza() != null ? a.getMinutosTardanza() : 0)
                .sum();
    }

    @Override
    public List<Integer> minutosTardanzaInjustificadaDelMes(Integer idEmpleado, int mes, int anio) {
        return asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio).stream()
                .filter(a -> !a.isJustificada())
                .filter(a -> "TARDANZA".equals(a.getEstado()) || "ASISTIO".equals(a.getEstado()))
                .map(a -> a.getMinutosTardanza() != null ? a.getMinutosTardanza() : 0)
                .filter(min -> min > 0)
                .toList();
    }

    @Override
    @Transactional
    public AsistenciaResponseDTO justificar(Integer idAsistencia, boolean justificada, String motivo) {
        AsistenciaDiaria a = asistenciaRepository.findById(idAsistencia)
                .orElseThrow(() -> new RuntimeException("Asistencia no encontrada."));
        if (justificada && (motivo == null || motivo.isBlank())) {
            throw new RuntimeException("Indique el motivo y el documento que respalda la justificación.");
        }
        a.setJustificada(justificada);
        a.setMotivoJustificacion(justificada ? motivo.trim() : null);
        AsistenciaResponseDTO dto = aDTO(asistenciaRepository.save(a));
        notificador.publicar("asistencias", "ACTUALIZADO", dto.getIdAsistencia(), dto);
        return dto;
    }

    @Override
    @Transactional
    public AsistenciaResponseDTO autorizarHorasExtra(Integer idAsistencia, Integer horas, String usuario) {
        AsistenciaDiaria a = asistenciaRepository.findById(idAsistencia)
                .orElseThrow(() -> new RuntimeException("Asistencia no encontrada."));
        if (horas != null && (horas < 0 || horas > 12)) {
            throw new RuntimeException("Las horas extras de un día deben estar entre 0 y 12.");
        }
        int disponibles = a.getMinutosExtra() != null ? a.getMinutosExtra() / 60 : 0;
        if (horas != null && horas > disponibles) {
            throw new RuntimeException("Ese día el reloj registró " + disponibles + " hora(s) completa(s) después de la salida: no se pueden autorizar " + horas + ".");
        }
        a.setHorasExtraAutorizadas(horas == null || horas == 0 ? null : horas);
        a.setAutorizadoPor(a.getHorasExtraAutorizadas() == null ? null : usuario);
        AsistenciaResponseDTO dto = aDTO(asistenciaRepository.save(a));
        notificador.publicar("asistencias", "ACTUALIZADO", dto.getIdAsistencia(), dto);
        return dto;
    }

    @Override
    public int[] horasExtraAutorizadasDelMes(Integer idEmpleado, int mes, int anio) {
        int al25 = 0, al35 = 0;
        for (AsistenciaDiaria a : asistenciaRepository.buscarAsistenciaPorMes(idEmpleado, mes, anio)) {
            int h = a.getHorasExtraAutorizadas() != null ? a.getHorasExtraAutorizadas() : 0;
            if (h <= 0) continue;
            al25 += Math.min(2, h);
            al35 += Math.max(0, h - 2);
        }
        return new int[]{al25, al35};
    }

    // Convertidor a DTO
    @Override
    public AsistenciaResponseDTO aDTO(AsistenciaDiaria a) {
        AsistenciaResponseDTO dto = new AsistenciaResponseDTO();
        dto.setIdAsistencia(a.getIdAsistencia());
        dto.setIdEmpleado(a.getEmpleado().getIdEmpleado());
        dto.setNombreEmpleado(a.getEmpleado().getNombres() + " " + a.getEmpleado().getApellidos());
        dto.setFecha(a.getFecha());
        dto.setHoraIngreso(a.getHoraIngreso());
        dto.setHoraSalida(a.getHoraSalida());
        dto.setEstado(a.getEstado());
        dto.setMinutosTardanza(a.getMinutosTardanza());
        dto.setMarcaIngreso(a.getMarcaIngreso());
        dto.setMarcaAlmuerzoInicio(a.getMarcaAlmuerzoInicio());
        dto.setMarcaAlmuerzoFin(a.getMarcaAlmuerzoFin());
        dto.setMarcaSalida(a.getMarcaSalida());
        dto.setHoraProgramadaEntrada(a.getHoraProgramadaEntrada());
        dto.setHoraProgramadaSalida(a.getHoraProgramadaSalida());
        dto.setMinutosExtra(a.getMinutosExtra());
        dto.setHorasExtraAutorizadas(a.getHorasExtraAutorizadas());
        dto.setAutorizadoPor(a.getAutorizadoPor());
        dto.setJustificada(a.isJustificada());
        dto.setMotivoJustificacion(a.getMotivoJustificacion());
        dto.setOrigen(a.getOrigen());
        dto.setObservacion(a.getObservacion());
        return dto;
    }
}
