package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "boleta_pago")
public class BoletaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idBoleta;

    @ManyToOne
    @JoinColumn(name = "id_planilla", nullable = false)
    private PlanillaCabecera planilla;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalIngresos;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalDescuentos;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalNeto; // Lo que se le deposita a la cuenta bancaria

    @Column(precision = 10, scale = 2)
    private BigDecimal renta5taRetenida; // Separamos la Renta de 5ta por tema de reportes SUNAT
}