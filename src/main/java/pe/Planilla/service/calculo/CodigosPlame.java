package pe.Planilla.service.calculo;

/**
 * Códigos de la Tabla 22 de SUNAT (PLAME - Ingresos, Tributos y Descuentos), verificados contra el documento oficial
 * "Tabla N° 22 Definición Conceptos PLAME".
 *
 * OJO: GRATIFICACION (0406) y BONIF_EXTRAORDINARIA (0312) figuran en esa tabla acotados a los ejercicios 2009-2014
 * (Ley 29351); la Ley 30334 los volvió permanentes. Confirma con tu contador / la ayuda del PDT PLAME cuál código
 * corresponde declarar hoy y, si cambia, edítalo aquí.
 */
public final class CodigosPlame {
    private CodigosPlame() {}

    // Ingresos
    public static final String BASICO = "0121";
    public static final String ASIGNACION_FAMILIAR = "0201";
    public static final String HORAS_EXTRAS_25 = "0105";
    public static final String HORAS_EXTRAS_35 = "0106";
    public static final String VACACIONES = "0118";
    public static final String GRATIFICACION = "0406";
    public static final String BONIF_EXTRAORDINARIA = "0312";
    // Liquidación (cese): verificados en la Tabla 22
    public static final String VACACIONES_TRUNCAS = "0114";
    public static final String GRATIFICACION_PROPORCIONAL = "0407";
    public static final String BONIF_EXTRAORDINARIA_PROPORCIONAL = "0313";
    // Variables de la planilla de la contadora (confirmar los códigos con el contador antes de declarar)
    public static final String COMISIONES = "0102";
    public static final String FERIADO_LABORADO = "0107";
    // No remunerativos (condición de trabajo): no entran a AFP/ONP, EsSalud ni Renta de 5ta
    public static final String MOVILIDAD = "0904";
    public static final String CONDICION_TRABAJO = "0915";
    /** Ingresos variables que se promedian para la gratificación y la CTS (suma del semestre / 6). */
    public static final java.util.Set<String> VARIABLES_PROMEDIABLES = java.util.Set.of(HORAS_EXTRAS_25, HORAS_EXTRAS_35, COMISIONES);

    // Descuentos al trabajador (no tributarios)
    public static final String ADELANTO = "0701";
    public static final String PRESTAMO = "0702";

    // Descuentos / tributos del trabajador
    public static final String AFP_COMISION = "0601";
    public static final String RENTA_5TA = "0605";
    public static final String AFP_PRIMA = "0606";
    public static final String ONP = "0607";
    public static final String AFP_APORTE = "0608";

    // Aportes del empleador
    public static final String ESSALUD = "0804";
}
