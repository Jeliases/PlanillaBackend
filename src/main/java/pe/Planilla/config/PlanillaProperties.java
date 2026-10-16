package pe.Planilla.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Parámetros legales que cambian con el tiempo. Se ajustan en application.properties (app.planilla.*)
 * o con variables de entorno, sin tocar el código. Los valores por defecto son los vigentes a oct-2026;
 * VERIFÍCALOS antes de cada cierre (RMV y UIT los fija el Estado; la RMV tiene un aumento anunciado).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.planilla")
public class PlanillaProperties {
    /** Remuneración Mínima Vital. Base de la asignación familiar (10%) y piso de EsSalud. */
    private BigDecimal rmv = new BigDecimal("1130");
    /** Unidad Impositiva Tributaria del año (deducción de 7 UIT y tramos de Renta 5ta). */
    private BigDecimal uit = new BigDecimal("5500");
    private BigDecimal asignacionFamiliarPct = new BigDecimal("0.10");
    /** Aporte del trabajador al Sistema Nacional de Pensiones. */
    private BigDecimal onpPct = new BigDecimal("0.13");
    /** Aporte del empleador a EsSalud (no se descuenta al trabajador). */
    private BigDecimal essaludPct = new BigDecimal("0.09");
    /** Bonificación extraordinaria sobre la gratificación (equivale al aporte a EsSalud). */
    private BigDecimal bonificacionExtraordinariaPct = new BigDecimal("0.09");
    /** RUC del empleador para el nombre del archivo PLAME. El valor por defecto es ficticio. */
    private String ruc = "20123456789";
}
