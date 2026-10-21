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
    private String idMarcador;     // ID en el reloj biométrico (null = el documento)
    private LocalDate fechaNacimiento;
    private LocalDate fechaIngreso;
    private String cargo;
    private String area;
    private String correo;
    private String telefono;
    private BigDecimal sueldoBase;
    private Integer horasJornada;  // 8 = jornada completa
    private String banco;
    private String cuentaBancaria;
    private String bancoCts;
    private String cuentaCts;
    private String supervisor;
    private String sede;
    private String cartera;
    private LocalDate fechaFinContrato;
    private String regimenLaboral; // Mostramos el nombre del régimen, no el ID
    private String estado;
    private String sistemaPensionario;
    private String afp;            // nombre de la AFP (null si es ONP)
    private String cuspp;
    private String tipoComisionAfp;
    private Boolean asignacionFamiliar;
    private LocalDate fechaCese;
    private String motivoCese;
}
