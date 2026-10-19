package pe.Planilla.model.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Audited
@Entity
@Table(name = "asistencia_diaria")
public class AsistenciaDiaria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAsistencia;

    private LocalDate fecha;

    // Se conservan por compatibilidad: son iguales a marcaIngreso / marcaSalida
    private LocalTime horaIngreso;
    private LocalTime horaSalida;

    /** ASISTIO, TARDANZA, FALTA, PERMISO o DESCANSO_MEDICO. */
    private String estado;
    private Integer minutosTardanza;

    // --- Las 4 marcaciones del reloj, en este orden ---
    private LocalTime marcaIngreso;
    private LocalTime marcaAlmuerzoInicio;
    private LocalTime marcaAlmuerzoFin;
    private LocalTime marcaSalida;

    /** Horario que correspondía ese día (del tareo o el horario por defecto). */
    private LocalTime horaProgramadaEntrada;
    private LocalTime horaProgramadaSalida;

    /** Minutos trabajados después de la hora de salida programada. Se pagan como horas extras solo si se autorizan. */
    private Integer minutosExtra;

    /** Horas extras autorizadas de ese día (la empresa exige aprobación previa). Null = no se pagan. */
    private Integer horasExtraAutorizadas;
    private String autorizadoPor;

    /** Tardanza o falta justificada con documentación válida: no se descuenta. */
    @Column(nullable = false)
    private boolean justificada = false;
    private String motivoJustificacion;

    /** MANUAL (digitada) o MARCADOR (importada del reloj). */
    private String origen;

    /** Avisos para revisar: "Sin marca de salida", "Marcaciones de más"... */
    private String observacion;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;
}
