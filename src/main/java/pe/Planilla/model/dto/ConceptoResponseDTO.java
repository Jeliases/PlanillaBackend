package pe.Planilla.model.dto;

import lombok.Data;

@Data
public class ConceptoResponseDTO {
    private Integer idConcepto;
    private String codigo;
    private String descripcion;
    private String tipo;
    private Boolean afectaRenta5ta;
    private Boolean afectaEssalud;
}