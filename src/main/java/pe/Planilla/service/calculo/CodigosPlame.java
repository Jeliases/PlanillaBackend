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

    // Descuentos / tributos del trabajador
    public static final String AFP_COMISION = "0601";
    public static final String RENTA_5TA = "0605";
    public static final String AFP_PRIMA = "0606";
    public static final String ONP = "0607";
    public static final String AFP_APORTE = "0608";

    // Aportes del empleador
    public static final String ESSALUD = "0804";
}
