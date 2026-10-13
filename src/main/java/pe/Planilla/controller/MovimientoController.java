package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.HorasExtrasRequestDTO;
import pe.Planilla.model.dto.MovimientoRequestDTO;
import pe.Planilla.model.dto.VacacionRequestDTO;
import pe.Planilla.model.entity.MovimientoPlanilla;
import pe.Planilla.service.MovimientoService;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    // Endpoint para registrar Bonos o Comisiones (Monto fijo)
    @PostMapping("/bonos")
    public ResponseEntity<MovimientoPlanilla> registrarBono(@RequestBody MovimientoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.registrarBonoOComision(dto));
    }

    // Endpoint para calcular Horas Extras exactas según Ley (Por cantidad de horas)
    @PostMapping("/horas-extras")
    public ResponseEntity<MovimientoPlanilla> registrarHorasExtras(@RequestBody HorasExtrasRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.calcularYRegistrarHorasExtras(dto));
    }

    // Endpoint para registrar el pago de vacaciones
    @PostMapping("/vacaciones")
    public ResponseEntity<MovimientoPlanilla> registrarVacaciones(@RequestBody VacacionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.registrarVacaciones(dto));
    }
}