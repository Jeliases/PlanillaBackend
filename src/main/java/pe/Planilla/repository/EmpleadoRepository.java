package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.Empleado;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {

    Optional<Empleado> findByDni(String dni);

    /** Todos los que comparten ese número de documento (puede coincidir entre un DNI y un CE). */
    List<Empleado> findAllByDni(String dni);

    /** Para no repetir el ID del reloj entre dos trabajadores. */
    Optional<Empleado> findFirstByIdMarcador(String idMarcador);
}
