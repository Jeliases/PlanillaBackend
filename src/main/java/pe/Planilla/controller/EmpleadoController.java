package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.service.EmpleadoService;

import java.util.List;

@RestController
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleadoService;

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
}