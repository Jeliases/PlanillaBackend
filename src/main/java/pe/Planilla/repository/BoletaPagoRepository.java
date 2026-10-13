package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.BoletaPago;

@Repository
public interface BoletaPagoRepository extends JpaRepository<BoletaPago, Integer> {
}