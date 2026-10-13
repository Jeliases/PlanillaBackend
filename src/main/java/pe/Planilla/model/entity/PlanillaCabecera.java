package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "planilla_cabecera")
public class PlanillaCabecera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPlanilla;

    @Column(unique = true, nullable = false, length = 7)
    private String periodo; // Guardaremos el mes y año en formato YYYY-MM (Ej: "2026-10")

    private LocalDate fechaCierre;

    private String estado; // "BORRADOR" o "CERRADA"
}