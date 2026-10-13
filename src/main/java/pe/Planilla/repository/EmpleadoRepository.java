package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.Empleado;

import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {

    Optional<Empleado> findByDni(String dni);
}