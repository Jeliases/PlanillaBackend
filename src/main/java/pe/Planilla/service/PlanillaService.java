package pe.Planilla.service;

import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.entity.PlanillaCabecera;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // ------------------------------------------------ desgloses y quincena (planilla de la contadora, 2026-10-21)

    /** Desglose de la gratificación de julio o diciembre: lo que sale en la boleta y en la constancia del trabajador. */
    record DetalleGratificacion(String periodo, LocalDate desde, LocalDate hasta, int meses, BigDecimal basico,
                                BigDecimal asignacionFamiliar, BigDecimal promedioVariable, BigDecimal computable,
                                BigDecimal factor, BigDecimal bruta, long faltas, BigDecimal descuentoFaltas,
                                BigDecimal neta, BigDecimal bonificacion, BigDecimal total) {}

    /** Desglose del depósito semestral de CTS (mayo o noviembre). */
    record DetalleCts(String periodo, LocalDate desde, LocalDate hasta, int meses, int dias, BigDecimal basico,
                      BigDecimal asignacionFamiliar, BigDecimal promedioVariable, BigDecimal gratificacionBase,
                      BigDecimal sextoGratificacion, BigDecimal computable, BigDecimal factor, long faltas, BigDecimal monto) {}

    /** Una fila del adelanto de la 1ra quincena: mitad del sueldo fijo menos la mitad de pensión y renta estimadas. */
    record Quincena(Integer idEmpleado, String empleado, String tipoDocumento, String documento, String banco, String cuenta,
                    BigDecimal basico, BigDecimal asignacionFamiliar, BigDecimal pension, BigDecimal renta5ta,
                    BigDecimal adelanto, boolean registrado) {}

    DetalleGratificacion detalleGratificacion(Integer idEmpleado, String periodo);
    DetalleCts detalleCts(Integer idEmpleado, String periodo);
    /** Propuesta del adelanto de quincena de cada trabajador del periodo (no guarda nada). */
    List<Quincena> calcularQuincena(String periodo);
    /** Registra (o actualiza) el adelanto como descuento 0701 del periodo: la boleta mensual lo resta y queda la 2da quincena. */
    List<Quincena> registrarQuincena(String periodo);
}
