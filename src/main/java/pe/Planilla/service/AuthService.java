package pe.Planilla.service;

import pe.Planilla.model.dto.AuthRequestDTO;
import pe.Planilla.model.dto.AuthResponseDTO;
import pe.Planilla.model.dto.UsuarioDTO;

import java.util.List;

public interface AuthService {
    AuthResponseDTO login(AuthRequestDTO request);

    /** El usuario autenticado cambia su propia contraseña. */
    void cambiarPassword(String username, String passwordActual, String passwordNueva);

    // --- Administración de usuarios (solo ADMIN)
    List<UsuarioDTO> listarUsuarios();
    UsuarioDTO crearUsuario(UsuarioDTO dto);
    UsuarioDTO actualizarUsuario(Integer id, UsuarioDTO dto, String usuarioQueEdita);
    /** Pone una contraseña temporal y obliga a cambiarla al entrar. */
    void reiniciarPassword(Integer id, String passwordTemporal);
}
