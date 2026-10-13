package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "concepto_remunerativo")
public class ConceptoRemunerativo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idConcepto;

    private String codigo;
    private String descripcion;
    private String tipo;
    private Boolean afectaRenta5ta;
    private Boolean afectaEssalud;
}