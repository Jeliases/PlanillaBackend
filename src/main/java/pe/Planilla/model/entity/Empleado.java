package pe.Planilla.model.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Audited
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

    /** ID con el que el trabajador marca en el reloj biométrico, si es distinto a su documento. Null = se usa el documento. */
    private String idMarcador;

    private LocalDate fechaNacimiento;
    private LocalDate fechaIngreso;
    private String cargo;
    private String area;
    private String correo;
    private String telefono;

    private BigDecimal sueldoBase;

    /**
     * Horas de la jornada diaria: 8 = tiempo completo (o null). Con menos horas el básico se paga proporcional
     * (sueldo a jornada completa / 8 x horas), como la planilla de la contadora; con menos de 4 no hay CTS.
     */
    private Integer horasJornada;

    /** Banco y cuenta donde se deposita el sueldo. */
    private String banco;
    private String cuentaBancaria;

    /** Banco y cuenta donde se deposita la CTS. */
    private String bancoCts;
    private String cuentaCts;

    /** Organización según la planilla de la contadora (opcionales): supervisor, sede y cartera; el centro de costos va en "area". */
    private String supervisor;
    private String sede;
    private String cartera;
    /** Fin del contrato a plazo fijo (null = indefinido). Informativo: no cesa al trabajador por sí solo. */
    private LocalDate fechaFinContrato;

    /** ACTIVO o CESADO. */
    private String estado;

    /** Último día trabajado. Desde el mes siguiente ya no entra en la planilla. */
    private LocalDate fechaCese;
    private String motivoCese;

    @ManyToOne
    @JoinColumn(name = "id_regimen")
    private RegimenLaboral regimenLaboral;

    /** "ONP", "AFP" o "NINGUNO" (jubilado / no aportante: sin descuento de pensión). Si es null (empleados antiguos) se trata como ONP. */
    private String sistemaPensionario;

    @ManyToOne
    @JoinColumn(name = "id_afp")
    private Afp afp;

    /** Código Único de Afiliación al SPP (solo AFP). */
    private String cuspp;

    /**
     * Tipo de comisión AFP: FLUJO (se descuenta 10% + prima + comisión por flujo) o MIXTA (solo 10% + prima: desde 2023 el
     * componente de flujo de la mixta es 0% y la comisión sobre saldo la paga el fondo). Null = FLUJO.
     */
    private String tipoComisionAfp;

    /** Tiene hijos menores/estudiando: cobra asignación familiar (10% de la RMV). */
    private Boolean asignacionFamiliar;
}
