package pe.Planilla.model.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Tabla de descuentos por tardanza (comunicado de la empresa). Cada tramo va por minutos de atraso respecto a la
 * hora de entrada programada: 9:01 a 9:20 son 1 a 20 minutos, y así. Se edita en la base sin tocar el código.
 */
@Data
@Audited
@Entity
@Table(name = "regla_tardanza")
public class ReglaTardanza {

    public static final String MONTO = "MONTO";          // monto fijo en soles
    public static final String MEDIO_DIA = "MEDIO_DIA";  // medio día de sueldo (sueldo / 30 / 2)
    public static final String DIA = "DIA";              // un día de sueldo (sueldo / 30)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRegla;

    private Integer orden;

    @Column(nullable = false)
    private Integer desdeMinuto;

    /** null = sin límite superior. */
    private Integer hastaMinuto;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(precision = 10, scale = 2)
    private BigDecimal monto;

    private String descripcion;
}
