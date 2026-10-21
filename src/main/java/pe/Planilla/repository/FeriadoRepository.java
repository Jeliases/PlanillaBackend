package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.Feriado;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeriadoRepository extends JpaRepository<Feriado, Integer> {

    List<Feriado> findByFechaBetweenOrderByFecha(LocalDate desde, LocalDate hasta);

    boolean existsByFecha(LocalDate fecha);
}
