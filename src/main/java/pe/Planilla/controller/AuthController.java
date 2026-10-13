package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.AuthRequestDTO;
import pe.Planilla.model.dto.AuthResponseDTO;
import pe.Planilla.service.AuthService;

@RestController
@RequestMapping("/api/auth") // Recuerda que en SecurityConfig pusimos que esta ruta sea PÚBLICA
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registrar")
    public ResponseEntity<AuthResponseDTO> registrar(@RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.registrar(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }
}