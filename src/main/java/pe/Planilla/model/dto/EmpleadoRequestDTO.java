package pe.Planilla.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmpleadoRequestDTO {
    private String tipoDocumento; // DNI (por defecto), CE, PASAPORTE, PTP o CSR
    private String nombres;
    private String apellidos;
    private String dni;           // número del documento de identidad
    private String idMarcador;    // ID en el reloj biométrico, si es distinto al documento
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
    private Integer idRegimen; // Solo pedimos el ID del régimen, no el objeto completo
    private String sistemaPensionario; // "ONP" (por defecto) o "AFP"
    private Integer idAfp;             // obligatorio si el sistema es AFP
    private String cuspp;
    private String tipoComisionAfp;    // FLUJO (por defecto) o MIXTA (no paga comisión por flujo)
    private Boolean asignacionFamiliar;
}
