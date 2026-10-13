package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmpleadoResponseDTO {
    private Integer idEmpleado;
    private String nombresCompletos;
    private String dni;
    private LocalDate fechaIngreso;
    private BigDecimal sueldoBase;
    private String regimenLaboral; // Mostramos el nombre del régimen, no el ID
    private String estado;
}