package pe.Planilla.model.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AsistenciaResponseDTO {
    private Integer idAsistencia;
    private Integer idEmpleado;
    private String nombreEmpleado; // Lo concatenamos para que el Frontend no sufra
    private LocalDate fecha;
    private LocalTime horaIngreso;
    private LocalTime horaSalida;
    private String estado;
    private Integer minutosTardanza;
}
