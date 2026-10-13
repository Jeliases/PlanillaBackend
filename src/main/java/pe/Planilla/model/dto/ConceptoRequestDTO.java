package pe.Planilla.model.dto;

import lombok.Data;

@Data
public class ConceptoRequestDTO {
    private String codigo;        // Ej: '0101' para Sueldo Básico
    private String descripcion;   // Ej: 'Sueldo Básico'
    private String tipo;          // INGRESO, DESCUENTO, APORTE
    private Boolean afectaRenta5ta;
    private Boolean afectaEssalud;
}