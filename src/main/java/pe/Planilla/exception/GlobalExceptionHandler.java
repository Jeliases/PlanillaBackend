package pe.Planilla.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.Planilla.model.dto.MensajeErrorDTO;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Atrapa todos los RuntimeException que lanzamos en los servicios
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<MensajeErrorDTO> manejarRuntimeException(RuntimeException ex) {
        MensajeErrorDTO error = new MensajeErrorDTO(ex.getMessage(), LocalDateTime.now());
        // Devuelve un error 400 (Bad Request) con el JSON limpio
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Atrapa cualquier otro error inesperado del sistema (NullPointer, base de datos caída, etc.)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeErrorDTO> manejarExcepcionGlobal(Exception ex) {
        MensajeErrorDTO error = new MensajeErrorDTO("Error interno del servidor: " + ex.getMessage(), LocalDateTime.now());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}