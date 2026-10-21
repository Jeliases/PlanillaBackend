package pe.Planilla.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.TipoDocumento;
import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.model.dto.ImportacionArchivoDTO;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO;
import pe.Planilla.service.ActualizacionEmpleadosService;
import pe.Planilla.service.EmpleadoService;
import pe.Planilla.service.EmpleadoLimpiezaService;
import pe.Planilla.service.ImportacionEmpleadosService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EmpleadoService empleadoService;
    private final EmpleadoLimpiezaService limpiezaService;
    private final ImportacionEmpleadosService importacionService;
    private final ActualizacionEmpleadosService actualizacionService;

    @GetMapping
    public ResponseEntity<List<EmpleadoResponseDTO>> listarEmpleados() {
        return ResponseEntity.ok(empleadoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpleadoResponseDTO> obtenerEmpleado(@PathVariable Integer id) {
        return ResponseEntity.ok(empleadoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<EmpleadoResponseDTO> registrarEmpleado(@RequestBody EmpleadoRequestDTO requestDTO) {
        EmpleadoResponseDTO nuevoEmpleado = empleadoService.registrarEmpleado(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoEmpleado);
    }

    @Data
    public static class CeseDTO {
        private java.time.LocalDate fechaCese;
        private String motivo;
    }

    // Editar datos (solo viajan los campos que cambian)
    @PutMapping("/{id}")
    public ResponseEntity<EmpleadoResponseDTO> actualizar(@PathVariable Integer id, @RequestBody EmpleadoRequestDTO dto) {
        return ResponseEntity.ok(empleadoService.actualizar(id, dto));
    }

    // Cese: desde el mes siguiente deja de entrar en la planilla
    @PostMapping("/{id}/cesar")
    public ResponseEntity<EmpleadoResponseDTO> cesar(@PathVariable Integer id, @RequestBody CeseDTO dto) {
        return ResponseEntity.ok(empleadoService.cesar(id, dto.getFechaCese(), dto.getMotivo()));
    }

    // Borrar un trabajador ingresado por error (solo si no tiene boletas; si no, usar /cesar). Borra tambien sus asistencias, movimientos y horarios.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        limpiezaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivar")
    public ResponseEntity<EmpleadoResponseDTO> reactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(empleadoService.reactivar(id));
    }

    // Tipos de documento para el combo del formulario (nombre, etiqueta y código SUNAT)
    @GetMapping("/tipos-documento")
    public ResponseEntity<List<Map<String, String>>> tiposDocumento() {
        return ResponseEntity.ok(Arrays.stream(TipoDocumento.values())
                .map(t -> Map.of("codigo", t.name(), "etiqueta", t.getEtiqueta(), "codigoSunat", t.getCodigoSunat()))
                .toList());
    }

    // Plantilla Excel para la carga masiva (con desplegables de régimen, AFP, tipo de documento...)
    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> descargarPlantilla() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDispositionFormData("attachment", "plantilla-empleados.xlsx");
        return new ResponseEntity<>(importacionService.generarPlantilla(), headers, HttpStatus.OK);
    }

    // Actualización masiva: Excel con los empleados actuales para editar y volver a subir
    @GetMapping("/actuales.xlsx")
    public ResponseEntity<byte[]> exportarActuales() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDispositionFormData("attachment", "empleados-actuales.xlsx");
        return new ResponseEntity<>(actualizacionService.exportarActuales(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/actualizar-masivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacionArchivoDTO> actualizarMasivo(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "true") boolean soloValidar) {
        return ResponseEntity.ok(actualizacionService.actualizar(archivo, soloValidar));
    }

    // Carga masiva. soloValidar=true (por defecto) solo revisa el archivo y no guarda nada.
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacionEmpleadosDTO> importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "true") boolean soloValidar) {
        return ResponseEntity.ok(importacionService.importar(archivo, soloValidar));
    }
}
