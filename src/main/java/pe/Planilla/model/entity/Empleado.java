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

    /** DNI, CE, PASAPORTE, PTP o CSR (ver TipoDocumento). Null en empleados antiguos = DNI. */
    private String tipoDocumento;

    private String nombres;
    private String apellidos;

    /** Número del documento de identidad (el nombre de la columna se conserva por compatibilidad). */
    private String dni;

    private LocalDate fechaNacimiento;
    private LocalDate fechaIngreso;
    private String cargo;
    private String area;
    private String correo;
    private String telefono;

    private BigDecimal sueldoBase;

    /** Banco y cuenta donde se deposita el sueldo. */
    private String banco;
    private String cuentaBancaria;

    /** Banco y cuenta donde se deposita la CTS. */
    private String bancoCts;
    private String cuentaCts;

    private String estado;

    @ManyToOne
    @JoinColumn(name = "id_regimen")
    private RegimenLaboral regimenLaboral;

    /** "ONP" o "AFP". Si es null (empleados antiguos) se trata como ONP. */
    private String sistemaPensionario;

    @ManyToOne
    @JoinColumn(name = "id_afp")
    private Afp afp;

    /** Código Único de Afiliación al SPP (solo AFP). */
    private String cuspp;

    /** Tiene hijos menores/estudiando: cobra asignación familiar (10% de la RMV). */
    private Boolean asignacionFamiliar;
}
