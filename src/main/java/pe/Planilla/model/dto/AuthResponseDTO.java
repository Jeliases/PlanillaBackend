package pe.Planilla.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private String rol;
    private String username;
    private String nombreCompleto;
    /** true: la pantalla debe pedir una contraseña nueva antes de seguir. */
    private boolean debeCambiarPassword;
}
