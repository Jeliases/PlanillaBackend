package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.ConceptoRemunerativo;

import java.util.Optional;

@Repository
public interface ConceptoRemunerativoRepository extends JpaRepository<ConceptoRemunerativo, Integer> {
    // Buscar concepto por su código SUNAT/PLAME (Ej: '0101')
    Optional<ConceptoRemunerativo> findByCodigo(String codigo);
}