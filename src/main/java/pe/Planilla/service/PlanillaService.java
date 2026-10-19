package pe.Planilla.service;

import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.entity.PlanillaCabecera;

import java.util.List;

public interface PlanillaService {
    /** Calcula (o recalcula) la boleta de un empleado en el periodo. Si ya existía, la actualiza: no duplica. */
    BoletaGeneradaDTO procesarBoletaMensual(Integer idEmpleado, String periodo);

    /** Procesa a todos los empleados ACTIVOS del periodo en una sola transacción (todo o nada). */
    List<BoletaGeneradaDTO> procesarPlanilla(String periodo);

    List<BoletaGeneradaDTO> listarBoletas(String periodo);

    BoletaGeneradaDTO obtenerBoleta(Integer idBoleta);

    /** Estado del periodo (BORRADOR/CERRADA, fecha de pago). Null si todavía no se procesó. */
    PlanillaCabecera estadoPlanilla(String periodo);

    /** Cierra el periodo: ya no se puede recalcular ni registrar movimientos. */
    PlanillaCabecera cerrar(String periodo, String usuario);

    /** Reabre con autorización (se guarda quién y por qué). */
    PlanillaCabecera reabrir(String periodo, String usuario, String motivo);
}
