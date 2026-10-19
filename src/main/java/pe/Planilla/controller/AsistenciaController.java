package pe.Planilla.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.dto.AsistenciaRequestDTO;
import pe.Planilla.model.dto.AsistenciaResponseDTO;
import pe.Planilla.model.dto.ImportacionArchivoDTO;
import pe.Planilla.service.AsistenciaService;
import pe.Planilla.service.ImportacionAsistenciaService;

import java.util.List;

@RestController
@RequestMapping("/api/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService asistenciaService;
    private final ImportacionAsistenciaService importacionService;

    @Data
    public static class JustificacionDTO {
        private boolean justificada = true;
        private String motivo;
    }

    @Data
    public static class HorasExtraDTO {
        private Integer horas;
    }

    // 1. Registrar una asistencia diaria
    @PostMapping
    public ResponseEntity<AsistenciaResponseDTO> registrarAsistencia(@RequestBody AsistenciaRequestDTO requestDTO) {
        AsistenciaResponseDTO nuevaAsistencia = asistenciaService.registrarTareo(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaAsistencia);
    }

    // 1b. Tareo de todos los empleados en un periodo (Ej: ?periodo=2026-10)
    @GetMapping
    public ResponseEntity<List<AsistenciaResponseDTO>> listarPorPeriodo(@RequestParam String periodo) {
        return ResponseEntity.ok(asistenciaService.listarPorPeriodo(periodo));
    }

    // 1c. Importar el Excel del marcador biométrico. soloValidar=true (por defecto) solo revisa y no guarda.
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacionArchivoDTO> importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "true") boolean soloValidar) {
        return ResponseEntity.ok(importacionService.importar(archivo, soloValidar));
    }

    // 1d. Justificar (o quitar la justificación de) una tardanza o falta con documentación válida
    @PutMapping("/{id}/justificar")
    public ResponseEntity<AsistenciaResponseDTO> justificar(@PathVariable Integer id, @RequestBody JustificacionDTO dto) {
        return ResponseEntity.ok(asistenciaService.justificar(id, dto.isJustificada(), dto.getMotivo()));
    }

    // 1e. Autorizar horas extras de un día (requisito de la empresa: aprobación previa)
    @PutMapping("/{id}/horas-extra")
    public ResponseEntity<AsistenciaResponseDTO> autorizarHorasExtra(@PathVariable Integer id, @RequestBody HorasExtraDTO dto,
                                                                     Authentication auth) {
        return ResponseEntity.ok(asistenciaService.autorizarHorasExtra(id, dto.getHoras(), auth.getName()));
    }

    // 2. Ver la lista de asistencias de un empleado en un mes específico
    @GetMapping("/empleado/{idEmpleado}")
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerPorMes(
            @PathVariable Integer idEmpleado,
            @RequestParam int mes,
            @RequestParam int anio) {
        return ResponseEntity.ok(asistenciaService.buscarPorEmpleadoYMes(idEmpleado, mes, anio));
    }

    // 3. Obtener solo el total de minutos de tardanza (se usará para la boleta)
    @GetMapping("/empleado/{idEmpleado}/tardanzas")
    public ResponseEntity<Integer> obtenerTotalTardanzas(
            @PathVariable Integer idEmpleado,
            @RequestParam int mes,
            @RequestParam int anio) {
        return ResponseEntity.ok(asistenciaService.calcularTotalMinutosTardanzaMes(idEmpleado, mes, anio));
    }
}
