package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.HorasExtrasRequestDTO;
import pe.Planilla.model.dto.MovimientoRequestDTO;
import pe.Planilla.model.dto.MovimientoResponseDTO;
import pe.Planilla.model.dto.VacacionRequestDTO;
import pe.Planilla.service.MovimientoService;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    // Movimientos de todos los empleados en un periodo (Ej: ?periodo=2026-10)
    @GetMapping
    public ResponseEntity<List<MovimientoResponseDTO>> listar(@RequestParam String periodo) {
        return ResponseEntity.ok(movimientoService.listarPorPeriodo(periodo));
    }

    // Endpoint para registrar Bonos, Comisiones o Descuentos (Monto fijo según el tipo del concepto)
    @PostMapping("/bonos")
    public ResponseEntity<MovimientoResponseDTO> registrarBono(@RequestBody MovimientoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.registrarBonoOComision(dto));
    }

    // Endpoint para calcular Horas Extras exactas según Ley (Por cantidad de horas)
    @PostMapping("/horas-extras")
    public ResponseEntity<List<MovimientoResponseDTO>> registrarHorasExtras(@RequestBody HorasExtrasRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.calcularYRegistrarHorasExtras(dto));
    }

    // Endpoint para registrar el pago de vacaciones
    @PostMapping("/vacaciones")
    public ResponseEntity<MovimientoResponseDTO> registrarVacaciones(@RequestBody VacacionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoService.registrarVacaciones(dto));
    }

    // Anular un movimiento mal registrado (no recalcula boletas ya procesadas: hay que volver a procesar la planilla)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        movimientoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
