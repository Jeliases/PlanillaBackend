package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.RegimenLaboral;

@Repository
public interface RegimenLaboralRepository extends JpaRepository<RegimenLaboral, Integer> {
}
