package pe.Planilla.model.dto;

import lombok.Data;

/** Días trabajados en feriado o día de descanso: se pagan (básico + asignación familiar) / 30 por día. */
@Data
public class FeriadoLaboradoRequestDTO {
    private Integer idEmpleado;
    private String periodo; // AAAA-MM
    private Integer dias;
}
