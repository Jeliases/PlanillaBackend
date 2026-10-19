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

    /** Minutos de cada tardanza del mes que NO está justificada (una por día con atraso). */
    List<Integer> minutosTardanzaInjustificadaDelMes(Integer idEmpleado, int mes, int anio);

    /** Marca o desmarca un día como justificado con documentación válida (no se descuenta). */
    AsistenciaResponseDTO justificar(Integer idAsistencia, boolean justificada, String motivo);

    /** Autoriza N horas extras de ese día (0 o null = no se pagan). Las primeras 2 van al 25% y el resto al 35%. */
    AsistenciaResponseDTO autorizarHorasExtra(Integer idAsistencia, Integer horas, String usuario);

    /** Horas extras autorizadas del mes, separadas en horas al 25% y al 35% (por día: 2 primeras al 25%). */
    int[] horasExtraAutorizadasDelMes(Integer idEmpleado, int mes, int anio);

    AsistenciaResponseDTO aDTO(pe.Planilla.model.entity.AsistenciaDiaria asistencia);
}
