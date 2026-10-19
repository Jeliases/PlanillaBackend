package pe.Planilla.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.AuthRequestDTO;
import pe.Planilla.model.dto.AuthResponseDTO;
import pe.Planilla.model.dto.UsuarioDTO;
import pe.Planilla.service.AuthService;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Data
    public static class CambioPasswordDTO {
        private String passwordActual;
        private String passwordNueva;
    }

    @Data
    public static class ReinicioPasswordDTO {
        private String passwordTemporal;
    }

    // Público
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Cualquier usuario autenticado cambia su propia contraseña
    @PostMapping("/cambiar-password")
    public ResponseEntity<Void> cambiarPassword(@RequestBody CambioPasswordDTO dto, Authentication auth) {
        authService.cambiarPassword(auth.getName(), dto.getPasswordActual(), dto.getPasswordNueva());
        return ResponseEntity.noContent().build();
    }

    // --- Administración de usuarios: solo ADMIN (ver SecurityConfig)
    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioDTO>> listarUsuarios() {
        return ResponseEntity.ok(authService.listarUsuarios());
    }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioDTO> crearUsuario(@RequestBody UsuarioDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.crearUsuario(dto));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<UsuarioDTO> actualizarUsuario(@PathVariable Integer id, @RequestBody UsuarioDTO dto, Authentication auth) {
        return ResponseEntity.ok(authService.actualizarUsuario(id, dto, auth.getName()));
    }

    @PostMapping("/usuarios/{id}/reiniciar-password")
    public ResponseEntity<Void> reiniciarPassword(@PathVariable Integer id, @RequestBody ReinicioPasswordDTO dto) {
        authService.reiniciarPassword(id, dto.getPasswordTemporal());
        return ResponseEntity.noContent().build();
    }
}
