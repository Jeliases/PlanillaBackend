package pe.Planilla.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Dos roles: ADMIN (el contador: hace todo) y GERENCIA (solo lectura). Todo GET de /api es lectura y lo ven los dos;
 * cualquier cambio (POST/PUT/DELETE) es solo del ADMIN, salvo el cambio de la propia contraseña.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    // Orígenes confiables (Angular). Se configuran en application.properties -> app.cors.allowed-origins
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        // El handshake del WebSocket es público; el JWT se valida en el CONNECT de STOMP (ver WebSocketConfig)
                        .requestMatchers("/ws/**").permitAll()
                        // La pantalla de Angular (index.html, js, css) es pública; los datos siguen protegidos por /api/**
                        .requestMatchers((RequestMatcher) request ->
                                "GET".equals(request.getMethod()) && !request.getRequestURI().startsWith("/api/")).permitAll()
                        // Cada usuario puede cambiar su propia contraseña
                        .requestMatchers("/api/auth/cambiar-password").authenticated()
                        // Administración de usuarios y auditoría: solo el contador, incluso para consultar
                        .requestMatchers("/api/auth/usuarios/**", "/api/auditoria/**").hasRole("ADMIN")
                        // Lectura: ambos roles
                        .requestMatchers(HttpMethod.GET, "/api/**").hasRole("LECTURA")
                        // Todo lo demás (crear, procesar, importar, cerrar, usuarios...): solo el contador
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // Sin token válido -> 401; con token pero sin permiso -> 403 con mensaje claro
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"mensaje\":\"Tu rol es de solo lectura: no puede hacer cambios.\"}");
                        }))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origenes = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
        configuration.setAllowedOriginPatterns(origenes); // admite comodines: https://*.trycloudflare.com
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Content-Disposition")); // para descargar archivos con su nombre
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
