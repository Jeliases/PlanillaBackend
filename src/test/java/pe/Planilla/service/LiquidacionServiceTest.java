package pe.Planilla.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.LiquidacionRequestDTO;
import pe.Planilla.model.dto.LiquidacionResponseDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.repository.BoletaPagoRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.service.calculo.CalculadoraPlanilla;
import pe.Planilla.service.impl.LiquidacionServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Liquidación por cese: cada beneficio solo por el tramo pendiente. Sin base de datos. */
class LiquidacionServiceTest {

    private final PlanillaProperties props = new PlanillaProperties();
    private final CalculadoraPlanilla calc = new CalculadoraPlanilla(props);
    private EmpleadoRepository empleadoRepo;
    private LiquidacionServiceImpl servicio;
    private Empleado empleado;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, bd(esperado).compareTo(actual), "esperado " + esperado + " pero fue " + actual);
    }

    @BeforeEach
    void preparar() {
        RegimenLaboral general = new RegimenLaboral();
        general.setIdRegimen(1);
        general.setNombre("Régimen General");
        general.setDiasVacaciones(30);
        general.setFactorLiquidacion(BigDecimal.ONE);
        general.setAsignacionFamiliar(true);

        empleado = new Empleado();
        empleado.setIdEmpleado(7);
        empleado.setNombres("Prueba");
        empleado.setApellidos("Cese");
        empleado.setDni("00000007");
        empleado.setSueldoBase(bd("3000"));
        empleado.setFechaIngreso(LocalDate.of(2024, 3, 1));
        empleado.setRegimenLaboral(general);
        empleado.setSistemaPensionario("ONP");
        empleado.setAsignacionFamiliar(false);

        empleadoRepo = mock(EmpleadoRepository.class);
        when(empleadoRepo.findById(7)).thenReturn(Optional.of(empleado));
        BoletaPagoRepository boletaRepo = mock(BoletaPagoRepository.class);
        when(boletaRepo.findByEmpleado_IdEmpleadoOrderByPlanilla_PeriodoDesc(anyInt())).thenReturn(List.of());
        servicio = new LiquidacionServiceImpl(empleadoRepo, boletaRepo, calc, props);
    }

    private LiquidacionResponseDTO liquidar(LocalDate cese) {
        LiquidacionRequestDTO dto = new LiquidacionRequestDTO();
        dto.setIdEmpleado(7);
        dto.setFechaCese(cese);
        return servicio.calcularLiquidacion(dto);
    }

    @Test
    void soloElTramoPendienteDeCadaBeneficio() {
        // Ingresó el 1-mar-2024, cesa el 15-oct-2026 (2 años, 7 meses y 15 días de servicio)
        LiquidacionResponseDTO r = liquidar(LocalDate.of(2026, 10, 15));
        assertEquals("2 años, 7 meses, 15 días", r.getTiempoServicio());

        // Vacaciones: desde el último aniversario (1-mar-2026): 7 meses y 15 días = 225/360 de 3,000 = 1,875.00
        assertMonto("1875.00", r.getMontoVacacionesTruncas());
        // Gratificación: semestre jul-dic, meses completos jul-sep = 3/6 de 3,000 = 1,500.00 + 9% = 135.00
        assertMonto("1500.00", r.getMontoGratificacionTrunca());
        assertMonto("135.00", r.getMontoBonificacionExtraordinaria());
        // CTS: desde el 1-may-2026: 5 meses y 15 días = 165/360 de (3,000 + 500) = 1,604.17
        assertMonto("1604.17", r.getMontoCtsTrunca());
        // ONP 13% solo sobre vacaciones: 243.75. Neto = 1,875 + 1,500 + 135 - 243.75 (la CTS se deposita aparte)
        assertMonto("243.75", r.getDescuentoPension());
        assertMonto("3266.25", r.getTotalNeto());
    }

    @Test
    void ceseEnElMismoAnioDeIngresoNoDuplicaTramos() {
        empleado.setFechaIngreso(LocalDate.of(2026, 8, 10));
        LiquidacionResponseDTO r = liquidar(LocalDate.of(2026, 10, 31));
        // Vacaciones: 10-ago a 31-oct = 2 meses y 22 días = 82/360 de 3,000 = 683.33
        assertMonto("683.33", r.getMontoVacacionesTruncas());
        // Gratificación: meses completos set y oct = 2/6 = 1,000.00
        assertMonto("1000.00", r.getMontoGratificacionTrunca());
        // CTS: desde el ingreso (después del 1-may): 82 días de (3,000 + 500) = 797.22
        assertMonto("797.22", r.getMontoCtsTrunca());
    }

    @Test
    void menosDeUnMesCompletoNoGeneraGratificacionNiCtsSinQuince() {
        empleado.setFechaIngreso(LocalDate.of(2026, 10, 5));
        LiquidacionResponseDTO r = liquidar(LocalDate.of(2026, 10, 14)); // 10 días
        assertMonto("0.00", r.getMontoGratificacionTrunca());
        assertMonto("0.00", r.getMontoCtsTrunca());
        assertMonto("83.33", r.getMontoVacacionesTruncas()); // 10/360 de 3,000
    }

    @Test
    void asignacionFamiliarEntraEnLaComputable() {
        empleado.setAsignacionFamiliar(true);
        LiquidacionResponseDTO r = liquidar(LocalDate.of(2026, 10, 15));
        assertMonto("3123.00", r.getRemuneracionComputable()); // 3,000 + 123 (10% de RMV 1,230)
    }

    @Test
    void regimenSinBeneficiosDevuelveCeros() {
        RegimenLaboral rxh = new RegimenLaboral();
        rxh.setNombre("Cuarta Categoría (RxH)");
        rxh.setFactorLiquidacion(BigDecimal.ZERO);
        rxh.setDiasVacaciones(0);
        empleado.setRegimenLaboral(rxh);
        LiquidacionResponseDTO r = liquidar(LocalDate.of(2026, 10, 15));
        assertMonto("0.00", r.getTotalNeto());
        assertFalse(r.getAvisos().isEmpty());
    }

    @Test
    void diasDelPeriodoConCese() {
        YearMonth oct = YearMonth.of(2026, 10);
        assertEquals(15, calc.diasDelPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 15), oct));
        assertEquals(11, calc.diasDelPeriodo(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20), oct));
        assertEquals(30, calc.diasDelPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 31), oct));
        assertEquals(0, calc.diasDelPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30), oct));
        assertEquals(30, calc.diasDelPeriodo(LocalDate.of(2025, 1, 1), null, oct));
    }
}
