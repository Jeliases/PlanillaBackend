package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LiquidacionResponseDTO {
    private String nombresEmpleado;
    private String regimen;
    private String tiempoServicio; // Ej: "1 años, 3 meses, 15 días"
    private BigDecimal montoGratificacionTrunca;
    private BigDecimal montoCtsTrunca;
    private BigDecimal montoVacacionesTruncas;
    private BigDecimal totalLiquidacion;
}