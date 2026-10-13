package pe.Planilla.model.dto;

import lombok.Data;

@Data
public class HorasExtrasRequestDTO {
    private Integer idEmpleado;
    private String periodo;
    private int horasAl25; // Cantidad de horas al 25% en el mes
    private int horasAl35; // Cantidad de horas al 35% en el mes
}