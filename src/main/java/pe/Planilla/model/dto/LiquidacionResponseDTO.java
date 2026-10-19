package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class LiquidacionResponseDTO {
    private Integer idEmpleado;
    private String nombresEmpleado;
    private String tipoDocumento;
    private String dni;
    private String regimen;
    private LocalDate fechaIngreso;
    private LocalDate fechaCese;
    private String tiempoServicio; // Ej: "1 años, 3 meses, 15 días"

    /** Sueldo + asignación familiar + promedio de variables (si hubo 3 meses seguidos). */
    private BigDecimal remuneracionComputable;
    private BigDecimal promedioVariables;

    // Beneficios truncos (solo el tramo pendiente de cada uno)
    private BigDecimal montoVacacionesTruncas;
    private String detalleVacaciones;
    private BigDecimal montoGratificacionTrunca;
    private String detalleGratificacion;
    private BigDecimal montoBonificacionExtraordinaria;
    private BigDecimal montoCtsTrunca;
    private String detalleCts;

    private BigDecimal totalBeneficios;
    /** AFP/ONP sobre vacaciones truncas (la gratificación y la CTS no aportan). */
    private BigDecimal descuentoPension;
    private String detallePension;
    private BigDecimal totalNeto;

    private List<LineaBoletaDTO> detalle;
    private List<String> avisos;
}
