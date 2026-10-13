package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.LiquidacionRequestDTO;
import pe.Planilla.model.dto.LiquidacionResponseDTO;
import pe.Planilla.service.LiquidacionService;

@RestController
@RequestMapping("/api/liquidaciones")
@RequiredArgsConstructor
public class LiquidacionController {

    private final LiquidacionService liquidacionService;

    @PostMapping("/calcular")
    public ResponseEntity<LiquidacionResponseDTO> calcularLiquidacion(@RequestBody LiquidacionRequestDTO dto) {
        return ResponseEntity.ok(liquidacionService.calcularLiquidacion(dto));
    }
}