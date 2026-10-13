package pe.Planilla.service;

import pe.Planilla.model.dto.ConceptoRequestDTO;
import pe.Planilla.model.dto.ConceptoResponseDTO;
import java.util.List;

public interface ConceptoService {
    List<ConceptoResponseDTO> listarTodos();
    ConceptoResponseDTO registrarConcepto(ConceptoRequestDTO requestDTO);
}