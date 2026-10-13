package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.service.PlanillaService;

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
}