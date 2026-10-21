package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.BoletaPago;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoletaPagoRepository extends JpaRepository<BoletaPago, Integer> {

    List<BoletaPago> findByPlanilla_Periodo(String periodo);

    Optional<BoletaPago> findByPlanilla_PeriodoAndEmpleado_IdEmpleado(String periodo, Integer idEmpleado);

    /** Todas las boletas de un empleado (para la liquidación: promedios de variables, última gratificación). */
    List<BoletaPago> findByEmpleado_IdEmpleadoOrderByPlanilla_PeriodoDesc(Integer idEmpleado);

    /** Boletas de un empleado en un año: prefijo "2026-" (para acumular ingresos y retenciones de Renta 5ta). */
    List<BoletaPago> findByPlanilla_PeriodoStartingWithAndEmpleado_IdEmpleado(String prefijoAnio, Integer idEmpleado);
    // Un trabajador con boletas procesadas no se puede borrar (solo cesar)
    boolean existsByEmpleado_IdEmpleado(Integer idEmpleado);
}
