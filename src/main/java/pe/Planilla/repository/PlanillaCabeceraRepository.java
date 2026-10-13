package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.PlanillaCabecera;

import java.util.Optional;

@Repository
public interface PlanillaCabeceraRepository extends JpaRepository<PlanillaCabecera, Integer> {
    Optional<PlanillaCabecera> findByPeriodo(String periodo);
}