package pe.Planilla.service;

import org.junit.jupiter.api.Test;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.entity.HorarioProgramado;
import pe.Planilla.model.entity.ReglaTardanza;
import pe.Planilla.service.calculo.CalculadoraAsistencia;
import pe.Planilla.service.calculo.CalculadoraAsistencia.DescuentoTardanzas;
import pe.Planilla.service.calculo.CalculadoraAsistencia.Marcas;
import pe.Planilla.service.calculo.CalculadoraAsistencia.ResultadoDia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de asistencia: marcaciones del reloj, tardanzas del comunicado de la empresa, faltas y horarios rotativos. */
class CalculadoraAsistenciaTest {

    private final PlanillaProperties props = new PlanillaProperties();
    private final CalculadoraAsistencia calc = new CalculadoraAsistencia(props);

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm.length() == 4 ? "0" + hhmm : hhmm);
    }

    private static List<LocalTime> horas(String... s) {
        return java.util.Arrays.stream(s).map(CalculadoraAsistenciaTest::t).toList();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, bd(esperado).compareTo(actual), "esperado " + esperado + " pero fue " + actual);
    }

    // Tabla del comunicado: 9:01-9:20 -> S/ 7; 9:21-10:00 -> S/ 15; 10:01-10:40 -> medio día
    private List<ReglaTardanza> tabla() {
        return List.of(regla(1, 20, ReglaTardanza.MONTO, "7.00"), regla(21, 60, ReglaTardanza.MONTO, "15.00"),
                regla(61, 100, ReglaTardanza.MEDIO_DIA, null));
    }

    private ReglaTardanza regla(int desde, Integer hasta, String tipo, String monto) {
        ReglaTardanza r = new ReglaTardanza();
        r.setDesdeMinuto(desde);
        r.setHastaMinuto(hasta);
        r.setTipo(tipo);
        r.setMonto(monto == null ? null : bd(monto));
        return r;
    }

    // ------------------------------------------------------------ marcaciones

    @Test
    void cuatroMarcasSonIngresoAlmuerzoYSalida() {
        Marcas m = calc.distribuir(horas("08:41", "13:09", "13:53", "19:04"));
        assertEquals(t("08:41"), m.ingreso());
        assertEquals(t("13:09"), m.almuerzoInicio());
        assertEquals(t("13:53"), m.almuerzoFin());
        assertEquals(t("19:04"), m.salida());
        assertFalse(m.incompleta());
    }

    @Test
    void dosMarcasSonIngresoYSalida() {
        Marcas m = calc.distribuir(horas("08:44", "18:08"));
        assertEquals(t("08:44"), m.ingreso());
        assertEquals(t("18:08"), m.salida());
        assertNull(m.almuerzoInicio());
        assertFalse(m.incompleta());
    }

    @Test
    void unaMarcaDeMananaEsIngresoYQuedaIncompleta() {
        Marcas m = calc.distribuir(horas("08:17"));
        assertEquals(t("08:17"), m.ingreso());
        assertNull(m.salida());
        assertTrue(m.incompleta());
    }

    @Test
    void tresMarcasQuedanIncompletasConIngresoYSalidaCorrectos() {
        Marcas m = calc.distribuir(horas("08:43", "13:45", "19:00"));
        assertEquals(t("08:43"), m.ingreso());
        assertEquals(t("19:00"), m.salida());
        assertTrue(m.incompleta());
    }

    @Test
    void marcasDeMasSeAvisanYElDobleToqueSeIgnora() {
        // 13:00 y 13:01 son un doble toque; quedan 4 marcas válidas
        Marcas doble = calc.distribuir(horas("07:56", "13:00", "13:01", "13:40", "18:05"));
        assertEquals(4, doble.cantidadLeidas());
        assertFalse(doble.incompleta());
        // Cinco marcas distintas: ingreso = primera, salida = última, se avisa
        Marcas cinco = calc.distribuir(horas("07:56", "13:00", "13:27", "13:40", "18:05"));
        assertEquals(t("07:56"), cinco.ingreso());
        assertEquals(t("18:05"), cinco.salida());
        assertTrue(cinco.incompleta());
    }

    // ------------------------------------------------------------- el día

    @Test
    void ingresoAlas901EsUnMinutoDeTardanza() {
        Marcas m = calc.distribuir(horas("09:01", "18:00"));
        ResultadoDia r = calc.evaluar(m, t("09:00"), t("18:00"), true);
        assertEquals("TARDANZA", r.estado());
        assertEquals(1, r.minutosTardanza());
    }

    @Test
    void ingresoAntesDeLaHoraNoEsTardanzaYSeCuentaElTiempoExtra() {
        Marcas m = calc.distribuir(horas("08:44", "19:30"));
        ResultadoDia r = calc.evaluar(m, t("09:00"), t("18:00"), true);
        assertEquals("ASISTIO", r.estado());
        assertEquals(0, r.minutosTardanza());
        assertEquals(90, r.minutosExtra());
    }

    @Test
    void sinMarcasUnDiaLaborableEsFalta() {
        ResultadoDia r = calc.evaluar(calc.distribuir(List.of()), t("09:00"), t("18:00"), true);
        assertEquals("FALTA", r.estado());
    }

    @Test
    void sinMarcasUnDiaDeDescansoNoGeneraRegistro() {
        assertNull(calc.evaluar(calc.distribuir(List.of()), t("09:00"), t("18:00"), false));
    }

    @Test
    void laTardanzaSeMideContraElHorarioRotativoDelDia() {
        // Turno temprano 8:00: entrar a las 8:25 son 25 minutos de atraso aunque el horario general sea 9:00
        ResultadoDia r = calc.evaluar(calc.distribuir(horas("08:25", "17:00")), t("08:00"), t("17:00"), true);
        assertEquals(25, r.minutosTardanza());
    }

    @Test
    void diasLaborablesSegunHorarioFeriadoYFinDeSemana() {
        LocalDate lunes = LocalDate.of(2026, 7, 6);
        LocalDate sabado = LocalDate.of(2026, 7, 4);
        assertTrue(calc.esLaborable(lunes, null, false));
        assertFalse(calc.esLaborable(sabado, null, false));   // sin horario, fin de semana descansa
        assertFalse(calc.esLaborable(lunes, null, true));     // feriado
        HorarioProgramado trabajaSabado = new HorarioProgramado();
        trabajaSabado.setHoraEntrada(t("09:00"));
        trabajaSabado.setHoraSalida(t("13:00"));
        assertTrue(calc.esLaborable(sabado, trabajaSabado, false));  // el tareo manda
        HorarioProgramado descansa = new HorarioProgramado();
        descansa.setDescanso(true);
        assertFalse(calc.esLaborable(lunes, descansa, false));
    }

    // --------------------------------------------------- descuento de tardanzas

    @Test
    void tardanzasPorTramosDelComunicado() {
        // 10 min -> 7.00 | 30 min -> 15.00 | 70 min (10:10) -> medio día de 3,000 = 50.00
        DescuentoTardanzas d = calc.descuentoTardanzas(List.of(10, 30, 70), bd("3000"), tabla());
        assertEquals(3, d.cantidad());
        assertMonto("72.00", d.montoTramos());
        assertMonto("0.00", d.montoDiaAdicional());
    }

    @Test
    void limitesExactosDeCadaTramo() {
        assertMonto("7.00", calc.descuentoTardanzas(List.of(20), bd("3000"), tabla()).montoTramos());   // 9:20
        assertMonto("15.00", calc.descuentoTardanzas(List.of(21), bd("3000"), tabla()).montoTramos());  // 9:21
        assertMonto("15.00", calc.descuentoTardanzas(List.of(60), bd("3000"), tabla()).montoTramos());  // 10:00
        assertMonto("50.00", calc.descuentoTardanzas(List.of(61), bd("3000"), tabla()).montoTramos());  // 10:01
        assertMonto("50.00", calc.descuentoTardanzas(List.of(100), bd("3000"), tabla()).montoTramos()); // 10:40
    }

    @Test
    void cincoTardanzasDescuentanUnDiaAdicional() {
        DescuentoTardanzas d = calc.descuentoTardanzas(List.of(5, 5, 5, 5, 5), bd("3000"), tabla());
        assertEquals(5, d.cantidad());
        assertMonto("35.00", d.montoTramos());          // 5 x 7.00
        assertMonto("100.00", d.montoDiaAdicional());   // 3,000 / 30
        assertMonto("135.00", d.total());
        // con 4 todavía no
        assertMonto("0.00", calc.descuentoTardanzas(List.of(5, 5, 5, 5), bd("3000"), tabla()).montoDiaAdicional());
    }

    @Test
    void masAllaDelUltimoTramoSeAplicaElUltimoYSeAvisa() {
        DescuentoTardanzas d = calc.descuentoTardanzas(List.of(130), bd("3000"), tabla()); // 11:10, sin regla
        assertEquals(1, d.fueraDeTabla());
        assertMonto("50.00", d.montoTramos());
    }

    @Test
    void sinTardanzasNoHayDescuento() {
        DescuentoTardanzas d = calc.descuentoTardanzas(List.of(), bd("3000"), tabla());
        assertEquals(0, d.cantidad());
        assertMonto("0.00", d.total());
    }
}
