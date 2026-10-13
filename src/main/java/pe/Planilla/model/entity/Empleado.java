package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "empleado")
public class Empleado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idEmpleado;

    private String nombres;
    private String apellidos;
    private String dni;
    private LocalDate fechaIngreso;
    private BigDecimal sueldoBase;
    private String cuentaBancaria;
    private String estado;

    @ManyToOne
    @JoinColumn(name = "id_regimen")
    private RegimenLaboral regimenLaboral;
}