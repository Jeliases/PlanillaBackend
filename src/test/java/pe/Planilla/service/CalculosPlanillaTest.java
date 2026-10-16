package pe.Planilla.service;

import org.junit.jupiter.api.Test;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.service.RentaQuintaService.DatosRenta5ta;
import pe.Planilla.service.calculo.CalculadoraPlanilla;
import pe.Planilla.service.calculo.CalculadoraPlanilla.Cts;
import pe.Planilla.service.calculo.CalculadoraPlanilla.Gratificacion;
import pe.Planilla.service.calculo.CalculadoraPlanilla.LineaPension;
import pe.Planilla.service.impl.RentaQuintaServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas de las reglas de cálculo con los parámetros por defecto (RMV 1130, UIT 5500). Sin base de datos. */
class CalculosPlanillaTest {

    private final PlanillaProperties props = new PlanillaProperties();
    private final CalculadoraPlanilla calc = new CalculadoraPlanilla(props);
    private final RentaQuintaServiceImpl renta = new RentaQuintaServiceImpl(props);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, bd(esperado).compareTo(actual), "esperado " + esperado + " pero fue " + actual);
    }

    // ------------------------------------------------------------- Días

    @Test
    void diasDelPeriodo() {
        YearMonth oct = YearMonth.of(2026, 10);
        assertEquals(30, calc.diasDelPeriodo(LocalDate.of(2025, 1, 10), oct));
        assertEquals(30, calc.diasDelPeriodo(LocalDate.of(2026, 10, 1), oct));
        assertEquals(16, calc.diasDelPeriodo(LocalDate.of(2026, 10, 16), oct)); // del 16 al 31
        assertEquals(0, calc.diasDelPeriodo(LocalDate.of(2026, 11, 1), oct));
    }

    @Test
    void diasPagadosDescuentaFaltasYVacaciones() {
        assertEquals(23, calc.diasPagados(30, 2, 5));
        assertEquals(0, calc.diasPagados(10, 20, 0));
        assertMonto("1600.00", calc.prorratear(bd("3000"), 16));
    }

    @Test
    void asignacionFamiliarEsDiezPorCientoDeLaRmv() {
        assertMonto("113.00", calc.asignacionFamiliarMensual());
    }

    // --------------------------------------------------------- Pensiones

    @Test
    void onpEsTrecePorCiento() {
        List<LineaPension> l = calc.pension("ONP", null, bd("2000"));
        assertEquals(1, l.size());
        assertEquals("0607", l.get(0).codigo());
        assertMonto("260.00", l.get(0).monto());
    }

    private Afp habitat() {
        Afp afp = new Afp();
        afp.setNombre("Habitat");
        afp.setAporteObligatorio(bd("0.1000"));
        afp.setComisionFlujo(bd("0.0147"));
        afp.setPrimaSeguro(bd("0.0137"));
        afp.setRemuneracionMaximaAsegurable(bd("12732.70"));
        return afp;
    }

    @Test
    void afpAporteComisionYPrima() {
        List<LineaPension> l = calc.pension("AFP", habitat(), bd("2000"));
        assertMonto("200.00", l.get(0).monto()); // aporte 10%
        assertMonto("29.40", l.get(1).monto());  // comisión 1.47%
        assertMonto("27.40", l.get(2).monto());  // prima 1.37%
    }

    @Test
    void afpPrimaTieneTopeDeRemuneracionMaximaAsegurable() {
        List<LineaPension> l = calc.pension("AFP", habitat(), bd("20000"));
        assertMonto("2000.00", l.get(0).monto());
        assertMonto("294.00", l.get(1).monto());
        assertMonto("174.44", l.get(2).monto()); // 12,732.70 x 1.37%, no 20,000 x 1.37%
    }

    @Test
    void afpSinFondoAsignadoFalla() {
        assertThrows(RuntimeException.class, () -> calc.pension("AFP", null, bd("2000")));
    }

    // ------------------------------------------------------ Gratificación

    @Test
    void mesesComputablesDeGratificacion() {
        YearMonth julio = YearMonth.of(2026, 7);
        YearMonth diciembre = YearMonth.of(2026, 12);
        assertEquals(6, calc.mesesComputablesGratificacion(LocalDate.of(2025, 5, 3), julio));
        assertEquals(3, calc.mesesComputablesGratificacion(LocalDate.of(2026, 3, 15), julio)); // abril-junio
        assertEquals(3, calc.mesesComputablesGratificacion(LocalDate.of(2026, 4, 1), julio));  // abril-junio
        assertEquals(3, calc.mesesComputablesGratificacion(LocalDate.of(2026, 9, 10), diciembre)); // octubre-diciembre
        assertEquals(0, calc.mesesComputablesGratificacion(LocalDate.of(2026, 12, 20), diciembre));
    }

    @Test
    void gratificacionProporcionalYBonificacion() {
        Gratificacion g = calc.gratificacion(bd("3000"), bd("1"), 3, 0);
        assertMonto("1500.00", g.neta());
        assertMonto("135.00", g.bonificacionExtraordinaria()); // 9%
    }

    @Test
    void gratificacionDescuentaUnCientoOchentavoPorFalta() {
        Gratificacion g = calc.gratificacion(bd("3000"), bd("1"), 6, 6);
        assertMonto("100.00", g.descuentoFaltas());
        assertMonto("2900.00", g.neta());
        assertMonto("261.00", g.bonificacionExtraordinaria());
    }

    @Test
    void gratificacionSegunRegimen() {
        assertMonto("1500.00", calc.gratificacion(bd("3000"), bd("0.5"), 6, 0).neta()); // pequeña empresa: la mitad
        assertMonto("0.00", calc.gratificacion(bd("3000"), bd("0"), 6, 0).neta());      // microempresa: no corresponde
    }

    // -------------------------------------------------------------- CTS

    @Test
    void ctsSemestreCompleto() {
        Cts cts = calc.cts(bd("2400"), bd("1"), LocalDate.of(2025, 1, 1), YearMonth.of(2026, 11), 0);
        assertEquals(6, cts.meses());
        assertMonto("1200.00", cts.monto());
        // El depósito de mayo cubre noviembre-abril
        assertMonto("1200.00", calc.cts(bd("2400"), bd("1"), LocalDate.of(2020, 1, 1), YearMonth.of(2026, 5), 0).monto());
    }

    @Test
    void ctsProporcionalConMesesYDias() {
        // Ingresó el 16/09: 1 mes y 16 días hasta el 31/10 = 46 días de 360
        Cts cts = calc.cts(bd("2400"), bd("1"), LocalDate.of(2026, 9, 16), YearMonth.of(2026, 11), 0);
        assertEquals(1, cts.meses());
        assertEquals(16, cts.dias());
        assertMonto("306.67", cts.monto());
        assertMonto("286.67", calc.cts(bd("2400"), bd("1"), LocalDate.of(2026, 9, 16), YearMonth.of(2026, 11), 3).monto());
    }

    @Test
    void ctsNoCorrespondeConMenosDeUnMes() {
        assertMonto("0.00", calc.cts(bd("2400"), bd("1"), LocalDate.of(2026, 10, 20), YearMonth.of(2026, 11), 0).monto());
    }

    // ------------------------------------------------------ Renta 5ta

    @Test
    void divisoresYMesesDeCorte() {
        assertEquals(0, renta.mesCorteRetenciones(1));
        assertEquals(3, renta.mesCorteRetenciones(4));
        assertEquals(4, renta.mesCorteRetenciones(7));
        assertEquals(7, renta.mesCorteRetenciones(8));
        assertEquals(11, renta.mesCorteRetenciones(12));
    }

    @Test
    void rentaQuintaEnero() {
        // Proyección: 5,000 + 5,000 x 11 + 2 gratificaciones de 5,450 = 70,900. Menos 7 UIT (38,500) = 32,400.
        // Impuesto: 27,500 x 8% + 4,900 x 14% = 2,886. Enero: /12.
        DatosRenta5ta d = new DatosRenta5ta(1, bd("0"), bd("5000"), bd("5000"), bd("1"), bd("0"));
        assertMonto("240.50", renta.calcularRetencionMensual(d));
    }

    @Test
    void rentaQuintaAgostoDescuentaRetencionesPrevias() {
        // Misma proyección anual: 40,450 previos + 5,000 + 4 x 5,000 + 1 gratificación pendiente de 5,450 = 70,900
        DatosRenta5ta d = new DatosRenta5ta(8, bd("40450"), bd("5000"), bd("5000"), bd("1"), bd("1400"));
        assertMonto("297.20", renta.calcularRetencionMensual(d)); // (2,886 - 1,400) / 5
    }

    @Test
    void rentaQuintaDiciembreAjustaTodo() {
        DatosRenta5ta d = new DatosRenta5ta(12, bd("60450"), bd("10450"), bd("5000"), bd("1"), bd("2000"));
        assertMonto("886.00", renta.calcularRetencionMensual(d)); // (2,886 - 2,000) / 1
    }

    @Test
    void sinRetencionSiNoSuperaLas7Uit() {
        DatosRenta5ta d = new DatosRenta5ta(1, bd("0"), bd("2000"), bd("2000"), bd("1"), bd("0"));
        assertMonto("0.00", renta.calcularRetencionMensual(d));
    }
}
