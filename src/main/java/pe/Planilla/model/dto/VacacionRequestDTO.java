package pe.Planilla.model.dto;

import lombok.Data;

@Data
public class VacacionRequestDTO {
    private Integer idEmpleado;
    private String periodo;
    private int diasGozados; // Cantidad de días que el trabajador se va de vacaciones
}