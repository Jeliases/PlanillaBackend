package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.model.dto.AuthRequestDTO;
import pe.Planilla.model.dto.AuthResponseDTO;
import pe.Planilla.model.dto.UsuarioDTO;
import pe.Planilla.model.entity.Usuario;
import pe.Planilla.repository.UsuarioRepository;
import pe.Planilla.service.AuthService;
import pe.Planilla.service.JwtService;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Set<String> ROLES = Set.of(Usuario.ROL_ADMIN, Usuario.ROL_GERENCIA);
    private static final int LARGO_MINIMO_PASSWORD = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponseDTO login(AuthRequestDTO request) {
        try {
            // Este método interno verifica que el usuario exista, esté activo y la contraseña sea correcta
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            throw new RuntimeException("El usuario está desactivado. Pida al administrador que lo active.");
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        return respuesta(usuario);
    }

    @Override
    @Transactional
    public void cambiarPassword(String username, String passwordActual, String passwordNueva) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new RuntimeException("La contraseña actual no es correcta.");
        }
        validarPassword(passwordNueva);
        if (passwordEncoder.matches(passwordNueva, usuario.getPassword())) {
            throw new RuntimeException("La contraseña nueva debe ser distinta a la actual.");
        }
        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuario.setDebeCambiarPassword(false);
        usuarioRepository.save(usuario);
    }

    // ------------------------------------------------------------ usuarios

    @Override
    public List<UsuarioDTO> listarUsuarios() {
        return usuarioRepository.findAll().stream().map(this::aDTO).toList();
    }

    @Override
    @Transactional
    public UsuarioDTO crearUsuario(UsuarioDTO dto) {
        if (dto.getUsername() == null || !dto.getUsername().trim().matches("[A-Za-z0-9._-]{3,30}")) {
            throw new RuntimeException("El usuario debe tener de 3 a 30 letras, números, punto, guion o guion bajo.");
        }
        String username = dto.getUsername().trim().toLowerCase();
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya existe.");
        }
        validarPassword(dto.getPassword());

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        usuario.setRol(rolValido(dto.getRol()));
        usuario.setNombreCompleto(limpiar(dto.getNombreCompleto()));
        usuario.setCorreo(limpiar(dto.getCorreo()));
        usuario.setActivo(dto.getActivo() == null || dto.getActivo());
        usuario.setDebeCambiarPassword(dto.getDebeCambiarPassword() == null || dto.getDebeCambiarPassword());
        return aDTO(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public UsuarioDTO actualizarUsuario(Integer id, UsuarioDTO dto, String usuarioQueEdita) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        boolean esElMismo = usuario.getUsername().equals(usuarioQueEdita);
        if (dto.getRol() != null) {
            String rol = rolValido(dto.getRol());
            if (esElMismo && !rol.equals(Usuario.ROL_ADMIN)) {
                throw new RuntimeException("No puedes quitarte a ti mismo el rol de administrador.");
            }
            usuario.setRol(rol);
        }
        if (dto.getActivo() != null) {
            if (esElMismo && !dto.getActivo()) {
                throw new RuntimeException("No puedes desactivar tu propio usuario.");
            }
            usuario.setActivo(dto.getActivo());
        }
        if (dto.getNombreCompleto() != null) usuario.setNombreCompleto(limpiar(dto.getNombreCompleto()));
        if (dto.getCorreo() != null) usuario.setCorreo(limpiar(dto.getCorreo()));
        return aDTO(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public void reiniciarPassword(Integer id, String passwordTemporal) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        validarPassword(passwordTemporal);
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setDebeCambiarPassword(true);
        usuarioRepository.save(usuario);
    }

    // ------------------------------------------------------------- ayudas

    private AuthResponseDTO respuesta(Usuario usuario) {
        return new AuthResponseDTO(jwtService.generateToken(usuario), usuario.getRol(), usuario.getUsername(),
                usuario.getNombreCompleto(), usuario.isDebeCambiarPassword());
    }

    private void validarPassword(String password) {
        if (password == null || password.length() < LARGO_MINIMO_PASSWORD) {
            throw new RuntimeException("La contraseña debe tener al menos " + LARGO_MINIMO_PASSWORD + " caracteres.");
        }
        if (!password.matches(".*[0-9].*") || !password.matches(".*[A-Za-z].*")) {
            throw new RuntimeException("La contraseña debe combinar letras y números.");
        }
    }

    private String rolValido(String rol) {
        String r = rol == null ? "" : rol.trim().toUpperCase();
        if (!ROLES.contains(r)) {
            throw new RuntimeException("El rol debe ser ADMIN (contador) o GERENCIA (solo lectura).");
        }
        return r;
    }

    private String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private UsuarioDTO aDTO(Usuario u) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setIdUsuario(u.getIdUsuario());
        dto.setUsername(u.getUsername());
        dto.setRol(u.getRol());
        dto.setNombreCompleto(u.getNombreCompleto());
        dto.setCorreo(u.getCorreo());
        dto.setActivo(u.isActivo());
        dto.setDebeCambiarPassword(u.isDebeCambiarPassword());
        return dto;
    }
}
