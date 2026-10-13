package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.AsistenciaDiaria;

import java.util.List;

@Repository
public interface AsistenciaDiariaRepository extends JpaRepository<AsistenciaDiaria, Integer> {

    // Busca las asistencias de un empleado en un mes específico (Para calcular descuentos/bonos)
    @Query("SELECT a FROM AsistenciaDiaria a WHERE a.empleado.idEmpleado = :idEmpleado AND MONTH(a.fecha) = :mes AND YEAR(a.fecha) = :anio")
    List<AsistenciaDiaria> buscarAsistenciaPorMes(@Param("idEmpleado") Integer idEmpleado, @Param("mes") int mes, @Param("anio") int anio);
}