package pe.Planilla.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Usuario del sistema. Roles: ADMIN (contador: hace todo) y GERENCIA (solo lectura).
 * El ADMIN tiene además ROLE_LECTURA, así una regla "solo lectura" la cumplen los dos.
 */
@Data
@Audited
@Entity
@Table(name = "usuario")
public class Usuario implements UserDetails {

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_GERENCIA = "GERENCIA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idUsuario;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String rol;

    private String nombreCompleto;
    private String correo;

    @Column(nullable = false)
    private boolean activo = true;

    /** true hasta que cambie la contraseña inicial que le asignó el administrador. */
    @Column(nullable = false)
    private boolean debeCambiarPassword = false;

    // === MÉTODOS DE SPRING SECURITY ===

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol), new SimpleGrantedAuthority("ROLE_LECTURA"));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return activo; }
}
