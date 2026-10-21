package pe.Planilla.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.Planilla.model.entity.AsistenciaDiaria;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaDiariaRepository extends JpaRepository<AsistenciaDiaria, Integer> {

    // Busca las asistencias de un empleado en un mes específico (Para calcular descuentos/bonos)
    @Query("SELECT a FROM AsistenciaDiaria a WHERE a.empleado.idEmpleado = :idEmpleado AND MONTH(a.fecha) = :mes AND YEAR(a.fecha) = :anio")
    List<AsistenciaDiaria> buscarAsistenciaPorMes(@Param("idEmpleado") Integer idEmpleado, @Param("mes") int mes, @Param("anio") int anio);

    // Un empleado tiene a lo sumo un registro por día
    Optional<AsistenciaDiaria> findFirstByEmpleado_IdEmpleadoAndFecha(Integer idEmpleado, LocalDate fecha);

    // Cantidad de días con un estado en un rango (todas, justificadas o no)
    long countByEmpleado_IdEmpleadoAndEstadoAndFechaBetween(Integer idEmpleado, String estado, LocalDate desde, LocalDate hasta);

    // Faltas que sí se descuentan: las justificadas con documentación no cuentan
    long countByEmpleado_IdEmpleadoAndEstadoAndJustificadaFalseAndFechaBetween(Integer idEmpleado, String estado, LocalDate desde, LocalDate hasta);

    // Todas las asistencias del rango (pantalla de tareo del mes)
    List<AsistenciaDiaria> findByFechaBetweenOrderByFechaDescIdAsistenciaDesc(LocalDate desde, LocalDate hasta);

    // Registros de un conjunto de empleados en un rango (para actualizar al importar el reloj)
    List<AsistenciaDiaria> findByFechaBetween(LocalDate desde, LocalDate hasta);
    // Limpieza: se usa al borrar un trabajador que nunca tuvo boletas
    void deleteByEmpleado_IdEmpleado(Integer idEmpleado);
}
