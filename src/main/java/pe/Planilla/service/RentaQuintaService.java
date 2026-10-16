package pe.Planilla.service;

import java.math.BigDecimal;

public interface RentaQuintaService {

    /**
     * Datos para la retención del mes.
     *
     * @param mes                    mes que se está procesando (1-12)
     * @param ingresosPrevios        ingresos brutos de los meses anteriores del año (reales o estimados)
     * @param ingresoMesActual       ingreso bruto del mes, incluida la gratificación si es julio/diciembre
     * @param remuneracionFijaMensual sueldo + asignación familiar de un mes completo (para proyectar los meses que faltan)
     * @param factorRegimen          proporción de gratificación del régimen (1.0 general, 0.5 pequeña empresa)
     * @param retencionesPrevias     retenciones ya efectuadas hasta el mes de corte (ver {@link #mesCorteRetenciones})
     */
    record DatosRenta5ta(int mes, BigDecimal ingresosPrevios, BigDecimal ingresoMesActual,
                         BigDecimal remuneracionFijaMensual, BigDecimal factorRegimen, BigDecimal retencionesPrevias) {}

    /** Retención de Renta de 5ta categoría del mes según el procedimiento de SUNAT (art. 40 del Reglamento de la LIR). */
    BigDecimal calcularRetencionMensual(DatosRenta5ta datos);

    /**
     * Hasta qué mes se restan las retenciones ya hechas: ene-mar ninguna; abril: ene-mar; may-jul: ene-abr;
     * agosto: ene-jul; set-nov: ene-ago; diciembre: ene-nov.
     */
    int mesCorteRetenciones(int mes);
}
