package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.repository.RegimenLaboralRepository;

import java.util.List;

@RestController
@RequestMapping("/api/regimenes")
@RequiredArgsConstructor
public class RegimenController {

    private final RegimenLaboralRepository regimenRepository;

    // Para llenar el combo "Régimen laboral" del formulario de empleados
    @GetMapping
    public ResponseEntity<List<RegimenLaboral>> listar() {
        return ResponseEntity.ok(regimenRepository.findAll());
    }
}
