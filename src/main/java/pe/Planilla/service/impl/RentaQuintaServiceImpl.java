package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.service.RentaQuintaService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RentaQuintaServiceImpl implements RentaQuintaService {

    // Divisor del impuesto restante según el mes (SUNAT): ene-mar 12, abr 9, may-jul 8, ago 5, set-nov 4, dic 1
    private static final int[] DIVISORES = {12, 12, 12, 9, 8, 8, 8, 5, 4, 4, 4, 1};
    private static final int[] MES_CORTE = {0, 0, 0, 3, 4, 4, 4, 7, 8, 8, 8, 11};

    private final PlanillaProperties props;

    @Override
    public int mesCorteRetenciones(int mes) {
        return MES_CORTE[mes - 1];
    }

    @Override
    public BigDecimal calcularRetencionMensual(DatosRenta5ta d) {
        BigDecimal uit = props.getUit();

        // 1. Proyección anual = lo ya percibido + el mes actual + los meses que faltan al sueldo actual + gratificaciones futuras
        BigDecimal mesesRestantes = new BigDecimal(12 - d.mes());
        BigDecimal gratificacionConBonif = d.remuneracionFijaMensual().multiply(d.factorRegimen())
                .multiply(BigDecimal.ONE.add(props.getBonificacionExtraordinariaPct()));
        BigDecimal gratificacionesPendientes = new BigDecimal(d.mes() < 7 ? 2 : (d.mes() < 12 ? 1 : 0));

        BigDecimal proyeccionAnual = d.ingresosPrevios()
                .add(d.ingresoMesActual())
                .add(d.remuneracionFijaMensual().multiply(mesesRestantes))
                .add(gratificacionConBonif.multiply(gratificacionesPendientes));

        // 2. Deducción de 7 UIT. Si no la supera no hay retención.
        BigDecimal rentaImponible = proyeccionAnual.subtract(uit.multiply(new BigDecimal("7")));
        if (rentaImponible.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal impuestoAnual = impuestoAnual(rentaImponible, uit);

        // 3. Fraccionamiento: ene-mar impuesto/12; desde abril se descuentan las retenciones hechas y se divide según el mes
        BigDecimal pendiente = d.mes() <= 3 ? impuestoAnual : impuestoAnual.subtract(d.retencionesPrevias());
        if (pendiente.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return pendiente.divide(new BigDecimal(DIVISORES[d.mes() - 1]), 2, RoundingMode.HALF_UP);
    }

    /** Escala progresiva: 8% hasta 5 UIT, 14% hasta 20, 17% hasta 35, 20% hasta 45 y 30% el exceso. */
    private BigDecimal impuestoAnual(BigDecimal rentaImponible, BigDecimal uit) {
        BigDecimal[] limites = {uit.multiply(new BigDecimal("5")), uit.multiply(new BigDecimal("15")),
                uit.multiply(new BigDecimal("15")), uit.multiply(new BigDecimal("10"))};
        BigDecimal[] tasas = {new BigDecimal("0.08"), new BigDecimal("0.14"), new BigDecimal("0.17"), new BigDecimal("0.20")};

        BigDecimal impuesto = BigDecimal.ZERO;
        BigDecimal restante = rentaImponible;
        for (int i = 0; i < limites.length && restante.signum() > 0; i++) {
            BigDecimal tramo = restante.min(limites[i]);
            impuesto = impuesto.add(tramo.multiply(tasas[i]));
            restante = restante.subtract(tramo);
        }
        if (restante.signum() > 0) {
            impuesto = impuesto.add(restante.multiply(new BigDecimal("0.30")));
        }
        return impuesto;
    }
}
