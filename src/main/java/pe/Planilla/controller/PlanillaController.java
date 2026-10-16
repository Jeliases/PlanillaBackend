package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.service.PlanillaService;

import java.util.List;

@RestController
@RequestMapping("/api/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaService planillaService;

    // Endpoint para generar la boleta de un empleado específico en un periodo (Ej: 2026-10)
    @PostMapping("/procesar-boleta")
    public ResponseEntity<BoletaGeneradaDTO> procesarBoletaMensual(
            @RequestParam Integer idEmpleado,
            @RequestParam String periodo) {

        return ResponseEntity.ok(planillaService.procesarBoletaMensual(idEmpleado, periodo));
    }

    // Procesa a todos los empleados activos del periodo de una sola vez
    @PostMapping("/{periodo}/procesar")
    public ResponseEntity<List<BoletaGeneradaDTO>> procesarPlanilla(@PathVariable String periodo) {
        return ResponseEntity.ok(planillaService.procesarPlanilla(periodo));
    }

    // Boletas ya generadas de un periodo
    @GetMapping("/{periodo}/boletas")
    public ResponseEntity<List<BoletaGeneradaDTO>> listarBoletas(@PathVariable String periodo) {
        return ResponseEntity.ok(planillaService.listarBoletas(periodo));
    }
}
