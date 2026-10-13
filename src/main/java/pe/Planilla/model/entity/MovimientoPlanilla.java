package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "movimiento_planilla")
public class MovimientoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idMovimiento;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    @ManyToOne
    @JoinColumn(name = "id_concepto", nullable = false)
    private ConceptoRemunerativo concepto;

    @Column(nullable = false, length = 7)
    private String periodo; // YYYY-MM

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
}