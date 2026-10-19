package pe.Planilla.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import pe.Planilla.service.JwtService;

import java.util.Arrays;
import java.util.List;

/**
 * Canal de tiempo real: Angular se conecta a ws://host:8081/ws con STOMP y se suscribe a /topic/{entidad}.
 * El servidor solo EMPUJA datos (/topic/*); los clientes no envían mensajes por aquí, usan la API REST.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origenes = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origenes); // admite comodines: https://*.trycloudflare.com
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Latidos cada 10 s: detectan conexiones muertas y mantienen vivo el socket detrás de proxys
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(wsHeartbeatScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    /**
     * Usa el mismo ObjectMapper que la API REST, así las fechas viajan igual por WebSocket
     * ("2026-10-16" y no [2026,10,16]).
     */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);
        converters.add(converter);
        return false; // no agregar los convertidores por defecto
    }

    /** Exige un JWT válido en el frame CONNECT (cabecera "Authorization: Bearer ..."). */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String auth = accessor.getFirstNativeHeader("Authorization");
                    if (auth == null || !auth.startsWith("Bearer ")) {
                        throw new MessagingException("Falta el token de autenticación.");
                    }
                    try {
                        String jwt = auth.substring(7);
                        UserDetails user = userDetailsService.loadUserByUsername(jwtService.extractUsername(jwt));
                        if (!jwtService.isTokenValid(jwt, user)) {
                            throw new MessagingException("Token inválido o vencido.");
                        }
                        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
                    } catch (JwtException | IllegalArgumentException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                        throw new MessagingException("Token inválido o vencido.");
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && accessor.getUser() == null) {
                    throw new MessagingException("Conexión no autenticada.");
                }
                return message;
            }
        });
    }
}
