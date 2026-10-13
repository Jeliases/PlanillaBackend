package pe.Planilla.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MensajeErrorDTO {
    private String mensaje;
    private LocalDateTime fechaHora;
}