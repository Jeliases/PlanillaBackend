package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Administradora de fondos de pensiones. Las tasas se guardan como fracción (0.1000 = 10%)
 * y cambian con el tiempo: se actualizan por PUT /api/afps/{id} según la tabla vigente de la SBS.
 */
@Data
@Entity
@Table(name = "afp")
public class Afp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAfp;

    @Column(unique = true, nullable = false)
    private String nombre;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal aporteObligatorio;

    /** Comisión sobre la remuneración (comisión por flujo). */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal comisionFlujo;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal primaSeguro;

    /** Tope de la remuneración sobre la que se calcula la prima (se actualiza cada trimestre). */
    @Column(precision = 10, scale = 2)
    private BigDecimal remuneracionMaximaAsegurable;
}
