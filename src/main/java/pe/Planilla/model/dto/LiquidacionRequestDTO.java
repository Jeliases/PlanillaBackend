package pe.Planilla.model.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LiquidacionRequestDTO {
    private Integer idEmpleado;
    private LocalDate fechaCese;
}