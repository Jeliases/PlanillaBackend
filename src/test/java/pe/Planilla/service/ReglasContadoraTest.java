package pe.Planilla.service;

import org.junit.jupiter.api.Test;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.service.RentaQuintaService.DatosRenta5ta;
import pe.Planilla.service.calculo.CalculadoraPlanilla;
import pe.Planilla.service.calculo.CalculadoraPlanilla.Cts;
import pe.Planilla.service.calculo.CalculadoraPlanilla.Gratificacion;
import pe.Planilla.service.impl.RentaQuintaServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Casos tomados de las hojas de cálculo de la contadora (CTS mayo 2026, gratificación julio 2026, planilla setiembre 2026 y
 * cálculo de quinta 2026). Los montos son los que figuran en esas hojas; aquí se comprueba que el sistema llega a lo mismo.
 */
class ReglasContadoraTest {

    private final PlanillaProperties props = new PlanillaProperties();
    private final CalculadoraPlanilla calc = new CalculadoraPlanilla(props);
    private final RentaQuintaServiceImpl renta = new RentaQuintaServiceImpl(props);

    @Test
    void jornadaParcialPagaElBasicoProporcional() {
        assertEquals(new BigDecimal("5000"), calc.basicoMensual(new BigDecimal("5000"), null));
        assertEquals(new BigDecimal("5000"), calc.basicoMensual(new BigDecimal("5000"), 8));
        assertEquals(new BigDecimal("2500.00"), calc.basicoMensual(new BigDecimal("5000"), 4));
        assertTrue(calc.tieneCts(8));
        assertTrue(calc.tieneCts(4));
        assertFalse(calc.tieneCts(3));
    }

    @Test
    void valorHoraSegunJornada() {
        // Planilla: 1800 / 30 / 8 = 7.50 (columna COSTO H)
        assertEquals(new BigDecimal("7.5000"), calc.valorHora(new BigDecimal("1800"), 8));
        // Media jornada: 1250 / 30 / 4
        assertEquals(new BigDecimal("10.4167"), calc.valorHora(new BigDecimal("1250"), 4));
    }

    @Test
    void sinAporteNoDescuentaPension() {
        assertTrue(calc.pension("NINGUNO", null, "FLUJO", new BigDecimal("15000")).isEmpty());
        assertEquals(1, calc.pension("ONP", null, "FLUJO", new BigDecimal("1800")).size());
    }

    @Test
    void feriadoLaboradoYPromedio() {
        // (5000 + 113) / 30 por día
        assertEquals(new BigDecimal("170.43"), calc.feriadoLaborado(new BigDecimal("5113"), 1));
        // Comisiones del semestre de la hoja de gratificación: suma / 6
        assertEquals(new BigDecimal("5890.03"), calc.promedioSemestral(new BigDecimal("35340.1978")));
        assertEquals(new BigDecimal("0.00"), calc.promedioSemestral(BigDecimal.ZERO));
    }

    @Test
    void gratificacionJulio2026ComoLaHoja() {
        // Fila 1: 5000 + 113, 6 meses -> 5113.00 y bonificación 460.17
        Gratificacion g = calc.gratificacion(new BigDecimal("5113"), BigDecimal.ONE, 6, 0);
        assertEquals(new BigDecimal("5113.00"), g.neta());
        assertEquals(new BigDecimal("460.17"), g.bonificacionExtraordinaria());

        // Fila 7: ingresó el 04/02/2026, sueldo 1250 -> 4 meses -> 833.33 y bonificación 75.00
        assertEquals(4, calc.mesesComputablesGratificacion(LocalDate.of(2026, 2, 4), YearMonth.of(2026, 7)));
        Gratificacion g2 = calc.gratificacion(new BigDecimal("1250"), BigDecimal.ONE, 4, 0);
        assertEquals(new BigDecimal("833.33"), g2.neta());
        assertEquals(new BigDecimal("75.00"), g2.bonificacionExtraordinaria());

        // Fila 12: ingresó el 01/04/2026, sueldo 4000 -> 3 meses -> 2000
        assertEquals(3, calc.mesesComputablesGratificacion(LocalDate.of(2026, 4, 1), YearMonth.of(2026, 7)));
        assertEquals(new BigDecimal("2000.00"), calc.gratificacion(new BigDecimal("4000"), BigDecimal.ONE, 3, 0).neta());

        // Con promedio de comisiones (fila 4): 3500 + 113 + 5890.03 = 9503.03 computable, 6 meses
        BigDecimal computable = new BigDecimal("3613").add(calc.promedioSemestral(new BigDecimal("35340.1978")));
        assertEquals(new BigDecimal("9503.03"), calc.gratificacion(computable, BigDecimal.ONE, 6, 0).neta());
    }

    @Test
    void ctsMayo2026ComoLaHoja() {
        // Fila 1: computable 5000 + 113 + 1/6 de 2556.50 (= 426.08) -> 6 meses -> 2769.54
        BigDecimal sexto = new BigDecimal("2556.50").divide(new BigDecimal("6"), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("426.08"), sexto);
        Cts c1 = calc.cts(new BigDecimal("5113").add(sexto), BigDecimal.ONE, LocalDate.of(2025, 10, 1), YearMonth.of(2026, 5), 0);
        assertEquals(6, c1.meses());
        assertEquals(0, c1.dias());
        assertEquals(new BigDecimal("2769.54"), c1.monto());

        // Fila 6: ingresó el 04/02/2026, computable 1250 -> 2 meses y 27 días -> 302.08
        Cts c2 = calc.cts(new BigDecimal("1250"), BigDecimal.ONE, LocalDate.of(2026, 2, 4), YearMonth.of(2026, 5), 0);
        assertEquals(2, c2.meses());
        assertEquals(27, c2.dias());
        assertEquals(new BigDecimal("302.08"), c2.monto());

        // Fila 11: ingresó el 01/04/2026, computable 4000 -> 1 mes -> 333.33
        Cts c3 = calc.cts(new BigDecimal("4000"), BigDecimal.ONE, LocalDate.of(2026, 4, 1), YearMonth.of(2026, 5), 0);
        assertEquals(1, c3.meses());
        assertEquals(new BigDecimal("333.33"), c3.monto());
    }

    @Test
    void rentaDeQuintaEneroCoincideConLaHoja() {
        // Hoja "5TA": 5113 mensual + 2 gratificaciones con bonificación (5573.17) = 72502.34 anual; menos 7 UIT (38500)
        // = 34002.34; impuesto 2200 + 910.33 = 3110.33; cuota mensual 259.19
        BigDecimal retencion = renta.calcularRetencionMensual(
                new DatosRenta5ta(1, BigDecimal.ZERO, new BigDecimal("5113"), new BigDecimal("5113"), BigDecimal.ONE, BigDecimal.ZERO));
        assertEquals(new BigDecimal("259.19"), retencion);
    }
}
