package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.dto.ConceptoRequestDTO;
import pe.Planilla.model.dto.ConceptoResponseDTO;
import pe.Planilla.model.entity.ConceptoRemunerativo;
import pe.Planilla.repository.ConceptoRemunerativoRepository;
import pe.Planilla.service.ConceptoService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConceptoServiceImpl implements ConceptoService {

    private final ConceptoRemunerativoRepository conceptoRepository;

    @Override
    public List<ConceptoResponseDTO> listarTodos() {
        return conceptoRepository.findAll().stream()
                .map(this::convertirAResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ConceptoResponseDTO registrarConcepto(ConceptoRequestDTO requestDTO) {
        // Validación estricta: No permitir códigos duplicados (evita errores en PLAME)
        if (conceptoRepository.findByCodigo(requestDTO.getCodigo()).isPresent()) {
            throw new RuntimeException("El concepto con código SUNAT " + requestDTO.getCodigo() + " ya existe.");
        }

        ConceptoRemunerativo concepto = new ConceptoRemunerativo();
        concepto.setCodigo(requestDTO.getCodigo());
        concepto.setDescripcion(requestDTO.getDescripcion());
        concepto.setTipo(requestDTO.getTipo().toUpperCase()); // Forzar mayúsculas por seguridad
        concepto.setAfectaRenta5ta(requestDTO.getAfectaRenta5ta());
        concepto.setAfectaEssalud(requestDTO.getAfectaEssalud());

        ConceptoRemunerativo conceptoGuardado = conceptoRepository.save(concepto);
        return convertirAResponseDTO(conceptoGuardado);
    }

    private ConceptoResponseDTO convertirAResponseDTO(ConceptoRemunerativo concepto) {
        ConceptoResponseDTO dto = new ConceptoResponseDTO();
        dto.setIdConcepto(concepto.getIdConcepto());
        dto.setCodigo(concepto.getCodigo());
        dto.setDescripcion(concepto.getDescripcion());
        dto.setTipo(concepto.getTipo());
        dto.setAfectaRenta5ta(concepto.getAfectaRenta5ta());
        dto.setAfectaEssalud(concepto.getAfectaEssalud());
        return dto;
    }
}