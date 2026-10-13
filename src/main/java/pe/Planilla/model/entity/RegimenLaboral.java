package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "regimen_laboral")
public class RegimenLaboral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRegimen;

    private String nombre;
    private Integer diasVacaciones;
    private BigDecimal factorLiquidacion;
}