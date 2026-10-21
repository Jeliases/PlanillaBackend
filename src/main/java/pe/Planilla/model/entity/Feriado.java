package pe.Planilla.model.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import lombok.Data;

import java.time.LocalDate;

/** Día feriado: la empresa no trabaja y no se marca falta. Editable por API (GET/POST/DELETE /api/feriados). */
@Data
@Audited
@Entity
@Table(name = "feriado")
public class Feriado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idFeriado;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(nullable = false)
    private String nombre;
}
