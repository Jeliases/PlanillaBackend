package pe.Planilla.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.entity.Feriado;
import pe.Planilla.repository.FeriadoRepository;

import java.time.LocalDate;
import java.util.List;

/** Calendario de feriados: en esos días no se marca falta. Los de 2026 se cargan al arrancar; aquí se agregan los demás. */
@RestController
@RequestMapping("/api/feriados")
@RequiredArgsConstructor
public class FeriadoController {

    private final FeriadoRepository feriadoRepository;

    @Data
    public static class FeriadoDTO {
        private LocalDate fecha;
        private String nombre;
    }

    @GetMapping
    public ResponseEntity<List<Feriado>> listar(@RequestParam(required = false) Integer anio) {
        int a = anio != null ? anio : LocalDate.now().getYear();
        return ResponseEntity.ok(feriadoRepository.findByFechaBetweenOrderByFecha(LocalDate.of(a, 1, 1), LocalDate.of(a, 12, 31)));
    }

    @PostMapping
    public ResponseEntity<Feriado> agregar(@RequestBody FeriadoDTO dto) {
        if (dto.getFecha() == null || dto.getNombre() == null || dto.getNombre().isBlank()) {
            throw new RuntimeException("Indique la fecha y el nombre del feriado.");
        }
        if (feriadoRepository.existsByFecha(dto.getFecha())) {
            throw new RuntimeException("Ya existe un feriado el " + dto.getFecha() + ".");
        }
        Feriado f = new Feriado();
        f.setFecha(dto.getFecha());
        f.setNombre(dto.getNombre().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(feriadoRepository.save(f));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        feriadoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
