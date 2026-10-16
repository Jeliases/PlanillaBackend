package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmpleadoResponseDTO {
    private Integer idEmpleado;
    private String tipoDocumento;
    private String nombresCompletos;
    private String dni;            // número del documento de identidad
    private LocalDate fechaNacimiento;
    private LocalDate fechaIngreso;
    private String cargo;
    private String area;
    private String correo;
    private String telefono;
    private BigDecimal sueldoBase;
    private String banco;
    private String cuentaBancaria;
    private String bancoCts;
    private String cuentaCts;
    private String regimenLaboral; // Mostramos el nombre del régimen, no el ID
    private String estado;
    private String sistemaPensionario;
    private String afp;            // nombre de la AFP (null si es ONP)
    private String cuspp;
    private Boolean asignacionFamiliar;
}
