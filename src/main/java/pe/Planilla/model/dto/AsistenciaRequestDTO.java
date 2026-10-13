package pe.Planilla.model.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AsistenciaRequestDTO {
    private Integer idEmpleado;
    private LocalDate fecha;
    private LocalTime horaIngreso;
    private LocalTime horaSalida;
    private String estado; // ASISTIO, FALTA, TARDANZA, PERMISO, DESCANSO_MEDICO
    private Integer minutosTardanza;
}