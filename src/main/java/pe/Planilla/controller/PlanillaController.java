package pe.Planilla.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.entity.PlanillaCabecera;
import pe.Planilla.service.PlanillaService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaService planillaService;

    @Data
    public static class ReaperturaDTO {
        private String motivo;
    }

    // Endpoint para generar la boleta de un empleado específico en un periodo (Ej: 2026-10)
    @PostMapping("/procesar-boleta")
    public ResponseEntity<BoletaGeneradaDTO> procesarBoletaMensual(
            @RequestParam Integer idEmpleado,
            @RequestParam String periodo) {

        return ResponseEntity.ok(planillaService.procesarBoletaMensual(idEmpleado, periodo));
    }

    // Procesa a todos los empleados del periodo de una sola vez
    @PostMapping("/{periodo}/procesar")
    public ResponseEntity<List<BoletaGeneradaDTO>> procesarPlanilla(@PathVariable String periodo) {
        return ResponseEntity.ok(planillaService.procesarPlanilla(periodo));
    }

    // Boletas ya generadas de un periodo
    @GetMapping("/{periodo}/boletas")
    public ResponseEntity<List<BoletaGeneradaDTO>> listarBoletas(@PathVariable String periodo) {
        return ResponseEntity.ok(planillaService.listarBoletas(periodo));
    }

    // Estado del periodo: BORRADOR / CERRADA (o "SIN_PROCESAR" si aún no existe)
    @GetMapping("/{periodo}/estado")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable String periodo) {
        PlanillaCabecera p = planillaService.estadoPlanilla(periodo);
        if (p == null) {
            return ResponseEntity.ok(Map.of("periodo", periodo, "estado", "SIN_PROCESAR"));
        }
        return ResponseEntity.ok(Map.of("periodo", periodo, "estado", p.getEstado(),
                "fechaCierre", p.getFechaCierre() != null ? p.getFechaCierre().toString() : "",
                "cerradaPor", p.getCerradaPor() != null ? p.getCerradaPor() : "",
                "fechaPago", p.getFechaPago() != null ? p.getFechaPago().toString() : "",
                "motivoReapertura", p.getMotivoReapertura() != null ? p.getMotivoReapertura() : ""));
    }

    @PostMapping("/{periodo}/cerrar")
    public ResponseEntity<PlanillaCabecera> cerrar(@PathVariable String periodo, Authentication auth) {
        return ResponseEntity.ok(planillaService.cerrar(periodo, auth.getName()));
    }

    @PostMapping("/{periodo}/reabrir")
    public ResponseEntity<PlanillaCabecera> reabrir(@PathVariable String periodo, @RequestBody ReaperturaDTO dto, Authentication auth) {
        return ResponseEntity.ok(planillaService.reabrir(periodo, auth.getName(), dto.getMotivo()));
    }
}
