package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "asistencia_diaria")
public class AsistenciaDiaria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAsistencia;

    private LocalDate fecha;
    private LocalTime horaIngreso;
    private LocalTime horaSalida;
    private String estado;
    private Integer minutosTardanza;

    @ManyToOne
    @JoinColumn(name = "id_empleado")
    private Empleado empleado;
}