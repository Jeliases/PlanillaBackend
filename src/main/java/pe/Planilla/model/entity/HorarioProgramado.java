package pe.Planilla.model.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** Horario planificado de un trabajador en un día (los asesores rotan de horario cada semana: el tareo). */
@Data
@Audited
@Entity
@Table(name = "horario_programado", uniqueConstraints = @UniqueConstraint(columnNames = {"id_empleado", "fecha"}))
public class HorarioProgramado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idHorario;

    @ManyToOne
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    @Column(nullable = false)
    private LocalDate fecha;

    private LocalTime horaEntrada;
    private LocalTime horaSalida;

    /** true = día de descanso: no se espera que venga ni se le marca falta. */
    @Column(nullable = false)
    private boolean descanso = false;

    private String comentario;
}
