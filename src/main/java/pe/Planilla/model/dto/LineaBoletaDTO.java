package pe.Planilla.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineaBoletaDTO {
    private String tipo;        // INGRESO, DESCUENTO o INFORMATIVO
    private String codigo;      // código SUNAT (puede ser null)
    private String descripcion;
    private BigDecimal monto;
}
