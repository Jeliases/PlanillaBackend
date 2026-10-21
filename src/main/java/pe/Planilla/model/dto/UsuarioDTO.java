package pe.Planilla.model.dto;

import lombok.Data;

/** Alta y edición de usuarios (solo ADMIN). La contraseña solo viaja al crear o al reiniciarla. */
@Data
public class UsuarioDTO {
    private Integer idUsuario;
    private String username;
    private String password;
    private String rol;            // ADMIN o GERENCIA
    private String nombreCompleto;
    private String correo;
    private Boolean activo;
    private Boolean debeCambiarPassword;
}
