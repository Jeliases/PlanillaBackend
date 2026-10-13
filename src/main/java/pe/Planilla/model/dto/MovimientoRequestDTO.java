package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MovimientoRequestDTO {
    private Integer idEmpleado;
    private String codigoConcepto; // Ej: '0301' para Bono
    private String periodo;        // Ej: '2026-10'
    private BigDecimal monto;
}