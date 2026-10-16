package pe.Planilla.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.repository.AfpRepository;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/afps")
@RequiredArgsConstructor
public class AfpController {

    private final AfpRepository afpRepository;

    @Data
    public static class AfpActualizarDTO {
        private BigDecimal aporteObligatorio;
        private BigDecimal comisionFlujo;
        private BigDecimal primaSeguro;
        private BigDecimal remuneracionMaximaAsegurable;
    }

    // Para el combo de AFP del formulario de empleados
    @GetMapping
    public ResponseEntity<List<Afp>> listar() {
        return ResponseEntity.ok(afpRepository.findAll());
    }

    // Actualizar tasas cuando la SBS las cambia (valores en fracción: 0.0147 = 1.47%). Solo ADMIN.
    @PutMapping("/{id}")
    public ResponseEntity<Afp> actualizar(@PathVariable Integer id, @RequestBody AfpActualizarDTO dto) {
        Afp afp = afpRepository.findById(id).orElseThrow(() -> new RuntimeException("AFP no encontrada."));
        validarTasa(dto.getAporteObligatorio());
        validarTasa(dto.getComisionFlujo());
        validarTasa(dto.getPrimaSeguro());
        if (dto.getAporteObligatorio() != null) afp.setAporteObligatorio(dto.getAporteObligatorio());
        if (dto.getComisionFlujo() != null) afp.setComisionFlujo(dto.getComisionFlujo());
        if (dto.getPrimaSeguro() != null) afp.setPrimaSeguro(dto.getPrimaSeguro());
        if (dto.getRemuneracionMaximaAsegurable() != null) afp.setRemuneracionMaximaAsegurable(dto.getRemuneracionMaximaAsegurable());
        return ResponseEntity.ok(afpRepository.save(afp));
    }

    private void validarTasa(BigDecimal tasa) {
        if (tasa != null && (tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0)) {
            throw new RuntimeException("Las tasas se envían como fracción entre 0 y 1 (Ej: 0.0147 para 1.47%).");
        }
    }
}
