package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MovimientoResponseDTO {
    private Integer idMovimiento;
    private Integer idEmpleado;
    private String empleado;
    private String codigoConcepto;
    private String concepto;
    private String tipo; // INGRESO o DESCUENTO
    private String periodo;
    private BigDecimal monto;
    private Integer cantidad; // días de vacaciones u horas extras (null en bonos/descuentos)
}
