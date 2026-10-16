package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BoletaGeneradaDTO {
    private Integer idBoleta;
    private Integer idEmpleado;
    private String periodo;
    private String nombresEmpleado;
    private String tipoDocumento;
    private String dni;
    private String regimen;
    private Integer diasTrabajados;
    private BigDecimal totalIngresos;
    private BigDecimal totalDescuentos;
    private BigDecimal totalNeto;
    private BigDecimal renta5taRetenida;
    private BigDecimal essaludEmpleador;
    private BigDecimal depositoCts;
    private List<LineaBoletaDTO> detalle;
}
