package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.MovimientoPlanilla;

import java.util.List;

@Repository
public interface MovimientoPlanillaRepository extends JpaRepository<MovimientoPlanilla, Integer> {
    // Buscar los movimientos de un empleado en un mes específico
    List<MovimientoPlanilla> findByEmpleado_IdEmpleadoAndPeriodo(Integer idEmpleado, String periodo);

    // Todos los movimientos del mes, los más recientes primero (pantalla de movimientos)
    List<MovimientoPlanilla> findByPeriodoOrderByIdMovimientoDesc(String periodo);

    // Un movimiento de un concepto dado (p. ej. el adelanto de quincena 0701) para no registrarlo dos veces
    java.util.Optional<MovimientoPlanilla> findFirstByEmpleado_IdEmpleadoAndPeriodoAndConcepto_Codigo(Integer idEmpleado, String periodo, String codigo);
    // Limpieza: se usa al borrar un trabajador que nunca tuvo boletas
    void deleteByEmpleado_IdEmpleado(Integer idEmpleado);
}
