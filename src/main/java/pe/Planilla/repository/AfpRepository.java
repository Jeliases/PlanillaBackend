package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.Afp;

import java.util.Optional;

@Repository
public interface AfpRepository extends JpaRepository<Afp, Integer> {
    Optional<Afp> findByNombre(String nombre);
}
