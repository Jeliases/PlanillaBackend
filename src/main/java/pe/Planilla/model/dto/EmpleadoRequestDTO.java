package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmpleadoRequestDTO {
    private String nombres;
    private String apellidos;
    private String dni;
    private LocalDate fechaIngreso;
    private BigDecimal sueldoBase;
    private String cuentaBancaria;
    private Integer idRegimen; // Solo pedimos el ID del régimen, no el objeto completo
}