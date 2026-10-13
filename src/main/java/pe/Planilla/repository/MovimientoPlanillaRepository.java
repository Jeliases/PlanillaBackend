package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.MovimientoPlanilla;

import java.util.List;

@Repository
public interface MovimientoPlanillaRepository extends JpaRepository<MovimientoPlanilla, Integer> {
    // Buscar los movimientos de un empleado en un mes específico
    List<MovimientoPlanilla> findByEmpleado_IdEmpleadoAndPeriodo(Integer idEmpleado, String periodo);
}

