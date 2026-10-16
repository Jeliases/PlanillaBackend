package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/** Una línea de la boleta: cada ingreso, descuento o aporte con su código SUNAT (si lo tiene). */
@Data
@Entity
@Table(name = "boleta_detalle")
public class BoletaDetalle {

    public static final String INGRESO = "INGRESO";
    public static final String DESCUENTO = "DESCUENTO";
    /** Informativo: no entra en el neto (aporte del empleador, depósito de CTS). */
    public static final String INFORMATIVO = "INFORMATIVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_boleta", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BoletaPago boleta;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(length = 10)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    private Integer orden;
}
