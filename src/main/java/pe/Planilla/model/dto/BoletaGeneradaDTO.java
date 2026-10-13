package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BoletaGeneradaDTO {
    private String periodo;
    private String nombresEmpleado;
    private String dni;
    private String regimen;
    private BigDecimal totalIngresos;
    private BigDecimal totalDescuentos;
    private BigDecimal totalNeto;
}