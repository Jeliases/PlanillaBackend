package pe.Planilla.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.AsistenciaDiariaRepository;
import pe.Planilla.repository.BoletaPagoRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.HorarioProgramadoRepository;
import pe.Planilla.repository.MovimientoPlanillaRepository;

/**
 * Limpieza de datos: borrar un trabajador ingresado por error (por ejemplo, tras una carga masiva equivocada).
 * Solo se permite si nunca tuvo una boleta procesada: una planilla pagada debe conservarse, y en ese caso lo que
 * corresponde es registrar el cese. Se borran también sus asistencias, movimientos y horarios; la auditoría (Envers)
 * guarda el rastro de lo borrado.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoLimpiezaService {

    private final EmpleadoRepository empleadoRepository;
    private final BoletaPagoRepository boletaRepository;
    private final AsistenciaDiariaRepository asistenciaRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final HorarioProgramadoRepository horarioRepository;
    private final EmpleadoService empleadoService;
    private final NotificadorTiempoReal notificador;

    @Transactional
    public void eliminar(Integer idEmpleado) {
        Empleado e = empleadoRepository.findById(idEmpleado)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        String nombre = e.getNombres() + " " + e.getApellidos();
        if (boletaRepository.existsByEmpleado_IdEmpleado(idEmpleado)) {
            throw new RuntimeException("«" + nombre + "» ya tiene boletas procesadas: no se puede borrar. "
                    + "Si dejó de trabajar, usa «Cesar»; la planilla ya pagada debe conservarse.");
        }
        asistenciaRepository.deleteByEmpleado_IdEmpleado(idEmpleado);
        movimientoRepository.deleteByEmpleado_IdEmpleado(idEmpleado);
        horarioRepository.deleteByEmpleado_IdEmpleado(idEmpleado);
        EmpleadoResponseDTO dto = empleadoService.aDTO(e);
        empleadoRepository.delete(e);
        notificador.publicar("empleados", "ELIMINADO", idEmpleado, dto); // las pantallas abiertas quitan la fila
    }
}
