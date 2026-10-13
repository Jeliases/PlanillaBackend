package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.AuthRequestDTO;
import pe.Planilla.model.dto.AuthResponseDTO;
import pe.Planilla.model.entity.Usuario;
import pe.Planilla.repository.UsuarioRepository;
import pe.Planilla.service.AuthService;
import pe.Planilla.service.JwtService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponseDTO registrar(AuthRequestDTO request) {
        if (usuarioRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya existe.");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(request.getUsername());
        // ENCRIPTAMOS LA CONTRASEÑA ANTES DE GUARDARLA
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setRol("ADMIN"); // Por defecto le daremos rol ADMIN

        usuarioRepository.save(usuario);

        // Generamos el token de una vez para que inicie sesión automáticamente
        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, usuario.getRol());
    }

    @Override
    public AuthResponseDTO login(AuthRequestDTO request) {
        // Este método interno verifica que el usuario exista y la contraseña sea correcta
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // Si llega aquí, es porque la contraseña es correcta
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, usuario.getRol());
    }
}