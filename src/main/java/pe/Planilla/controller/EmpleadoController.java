package pe.Planilla.controller;

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
import pe.Planilla.model.dto.ImportacionEmpleadosDTO;
import pe.Planilla.service.EmpleadoService;
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
    private final ImportacionEmpleadosService importacionService;

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

    // Carga masiva. soloValidar=true (por defecto) solo revisa el archivo y no guarda nada.
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacionEmpleadosDTO> importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "true") boolean soloValidar) {
        return ResponseEntity.ok(importacionService.importar(archivo, soloValidar));
    }
}
