package pe.Planilla.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Punto único para avisar a los clientes que algo cambió.
 *
 * Los servicios llaman a {@link #publicar}. El mensaje se envía por WebSocket recién DESPUÉS del commit
 * (así nunca se anuncia algo que luego se revierte). Si no hay transacción abierta, se envía de inmediato.
 */
@Component
@RequiredArgsConstructor
public class NotificadorTiempoReal {

    public static final String PREFIJO_TOPIC = "/topic/";

    private final ApplicationEventPublisher publisher;
    private final SimpMessagingTemplate messagingTemplate;

    public void publicar(String entidad, String accion, Object id, Object data) {
        publisher.publishEvent(new EventoTiempoReal(entidad, accion, id, data, Instant.now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void enviar(EventoTiempoReal evento) {
        messagingTemplate.convertAndSend(PREFIJO_TOPIC + evento.entidad(), evento);
    }
}
