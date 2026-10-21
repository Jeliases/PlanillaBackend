package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.dto.ImportacionArchivoDTO;
import pe.Planilla.service.ImportacionHorariosService;

@RestController
@RequestMapping("/api/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportacionHorariosService horariosService;

    // Plantilla Excel del tareo (horario programado por trabajador y día)
    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> plantilla() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDispositionFormData("attachment", "plantilla-horarios.xlsx");
        return new ResponseEntity<>(horariosService.generarPlantilla(), headers, HttpStatus.OK);
    }

    // soloValidar=true (por defecto) solo revisa el archivo y no guarda nada
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacionArchivoDTO> importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "true") boolean soloValidar) {
        return ResponseEntity.ok(horariosService.importar(archivo, soloValidar));
    }
}
