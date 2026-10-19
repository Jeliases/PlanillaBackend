package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Audited
@Entity
@Table(name = "planilla_cabecera")
public class PlanillaCabecera {

    public static final String BORRADOR = "BORRADOR";
    public static final String CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPlanilla;

    @Column(unique = true, nullable = false, length = 7)
    private String periodo; // Guardaremos el mes y año en formato YYYY-MM (Ej: "2026-10")

    private LocalDate fechaCierre;

    private String estado; // BORRADOR o CERRADA

    /** Fecha de pago que sale en la boleta (por defecto el último día del mes). */
    private LocalDate fechaPago;

    private String cerradaPor;
    private LocalDateTime reabiertaEn;
    private String reabiertaPor;
    /** Quién autorizó la reapertura (la empresa exige autorización del contador y gerencia). */
    private String motivoReapertura;
}
