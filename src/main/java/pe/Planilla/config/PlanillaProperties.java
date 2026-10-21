package pe.Planilla.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Parámetros legales y de la empresa que cambian con el tiempo. Se ajustan en application.properties (app.planilla.*)
 * o con variables de entorno, sin tocar el código. Los valores por defecto son los vigentes a oct-2026;
 * VERIFÍCALOS antes de cada cierre (la RMV y la UIT las fija el Estado).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.planilla")
public class PlanillaProperties {
    /** Remuneración Mínima Vital (S/ 1,230 desde el 1-oct-2026, D.S. 015-2026-TR). Base de la asignación familiar y piso de EsSalud. */
    private BigDecimal rmv = new BigDecimal("1230");
    /** Unidad Impositiva Tributaria del año (deducción de 7 UIT y tramos de Renta 5ta). */
    private BigDecimal uit = new BigDecimal("5500");
    private BigDecimal asignacionFamiliarPct = new BigDecimal("0.10");
    /** Aporte del trabajador al Sistema Nacional de Pensiones. */
    private BigDecimal onpPct = new BigDecimal("0.13");
    /** Aporte del empleador a EsSalud (no se descuenta al trabajador). */
    private BigDecimal essaludPct = new BigDecimal("0.09");
    /** Bonificación extraordinaria sobre la gratificación (equivale al aporte a EsSalud). */
    private BigDecimal bonificacionExtraordinariaPct = new BigDecimal("0.09");
    /** RUC del empleador (nombre del archivo PLAME y boleta). */
    private String ruc = "20604919321";
    private String razonSocial = "INFORMA PERU S.A.C.";
    /** Dirección fiscal que sale en la boleta (igual que en la boleta modelo). */
    private String direccion = "CAL. HUAMACHUCO NRO. 1408 DPTO. 704 LIMA - LIMA - JESUS MARIA";
    private String lugarPago = "LIMA";
    /** Representante legal que firma las constancias de gratificación y CTS (dato personal: solo por variable de entorno). */
    private String representanteLegal = "";
    private String representanteDocumento = "";

    // --- Asistencia (comunicado de tardanzas) ---
    /** Horario cuando el trabajador no tiene uno programado en el tareo. */
    private LocalTime horaEntradaDefecto = LocalTime.of(9, 0);
    private LocalTime horaSalidaDefecto = LocalTime.of(18, 0);
    /** Con este número de tardanzas (injustificadas) en el mes se descuenta un día adicional. */
    private int tardanzasParaDiaAdicional = 5;
}
