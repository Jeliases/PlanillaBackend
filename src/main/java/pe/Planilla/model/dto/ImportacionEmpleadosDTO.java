package pe.Planilla.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Resultado de revisar (o importar) un Excel de empleados. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportacionEmpleadosDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorFila {
        private int fila;        // número de fila en el Excel (la 1 es el encabezado)
        private String mensaje;
    }

    private int totalFilas;
    private int filasValidas;
    /** true solo si se guardaron los empleados (no hay errores y no era "solo validar"). */
    private boolean importado;
    private List<ErrorFila> errores;
    /** Los empleados que se crearían (vista previa) o que se crearon. */
    private List<EmpleadoResponseDTO> empleados;
}
