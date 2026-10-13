package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.ConceptoRequestDTO;
import pe.Planilla.model.dto.ConceptoResponseDTO;
import pe.Planilla.service.ConceptoService;

import java.util.List;

@RestController
@RequestMapping("/api/conceptos")
@RequiredArgsConstructor
public class ConceptoController {

    private final ConceptoService conceptoService;

    @GetMapping
    public ResponseEntity<List<ConceptoResponseDTO>> listarConceptos() {
        return ResponseEntity.ok(conceptoService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<ConceptoResponseDTO> registrarConcepto(@RequestBody ConceptoRequestDTO requestDTO) {
        ConceptoResponseDTO nuevoConcepto = conceptoService.registrarConcepto(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoConcepto);
    }
}