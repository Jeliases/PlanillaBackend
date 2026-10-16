package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /** Días efectivamente pagados en el mes (base 30), descontando faltas y vacaciones. */
    private Integer diasTrabajados;

    /** Aporte del empleador a EsSalud: costo de la empresa, no se descuenta al trabajador. */
    @Column(precision = 10, scale = 2)
    private BigDecimal essaludEmpleador;

    /** CTS a depositar en mayo/noviembre. No forma parte del neto: va al banco depositario. */
    @Column(precision = 10, scale = 2)
    private BigDecimal depositoCts;

    @OneToMany(mappedBy = "boleta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BoletaDetalle> detalle = new ArrayList<>();
}
