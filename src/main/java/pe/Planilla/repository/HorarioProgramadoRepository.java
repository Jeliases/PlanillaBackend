package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.HorarioProgramado;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HorarioProgramadoRepository extends JpaRepository<HorarioProgramado, Integer> {

    Optional<HorarioProgramado> findFirstByEmpleado_IdEmpleadoAndFecha(Integer idEmpleado, LocalDate fecha);

    List<HorarioProgramado> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
