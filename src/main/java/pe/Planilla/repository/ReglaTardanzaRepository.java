package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.ReglaTardanza;

import java.util.List;

@Repository
public interface ReglaTardanzaRepository extends JpaRepository<ReglaTardanza, Integer> {

    List<ReglaTardanza> findAllByOrderByDesdeMinutoAsc();
}
