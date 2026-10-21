package pe.Planilla.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO.ErrorFila;

import java.util.List;
import java.util.Map;

/** Resultado de revisar (o importar) un Excel de asistencia o de horarios. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportacionArchivoDTO {
    private int totalFilas;
    private int filasValidas;
    /** true solo si se guardó (no hay errores y no era "solo validar"). */
    private boolean importado;
    /** Problemas que impiden importar. */
    private List<ErrorFila> errores;
    /** Cosas para revisar que no impiden importar. */
    private List<String> advertencias;
    /** Cifras del archivo (días, tardanzas, faltas, trabajadores sin coincidencia...). */
    private Map<String, Object> resumen;
}
