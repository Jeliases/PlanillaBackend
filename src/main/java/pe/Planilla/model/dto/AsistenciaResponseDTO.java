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

    // Marcaciones del reloj (en este orden) y horario que correspondía
    private LocalTime marcaIngreso;
    private LocalTime marcaAlmuerzoInicio;
    private LocalTime marcaAlmuerzoFin;
    private LocalTime marcaSalida;
    private LocalTime horaProgramadaEntrada;
    private LocalTime horaProgramadaSalida;
    private Integer minutosExtra;
    private Integer horasExtraAutorizadas;
    private String autorizadoPor;

    private boolean justificada;
    private String motivoJustificacion;
    private String origen;
    private String observacion;
}
