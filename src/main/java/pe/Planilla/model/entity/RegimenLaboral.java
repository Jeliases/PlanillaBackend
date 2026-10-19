package pe.Planilla.model.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Audited
@Entity
@Table(name = "regimen_laboral")
public class RegimenLaboral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRegimen;

    private String nombre;
    private Integer diasVacaciones;
    private BigDecimal factorLiquidacion;

    /** El régimen paga asignación familiar (solo el Régimen General, según la empresa). */
    private Boolean asignacionFamiliar;
}
