package pe.Planilla.service.impl;

import org.springframework.stereotype.Service;
import pe.Planilla.service.RentaQuintaService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RentaQuintaServiceImpl implements RentaQuintaService {

    // En un sistema 100% administrable, este valor debería venir de una tabla 'parametros_sistema'
    // Para el 2024/2026, usaremos un valor referencial oficial (Ej: S/ 5150)
    private final BigDecimal VALOR_UIT = new BigDecimal("5150");

    @Override
    public BigDecimal calcularRetencionMensual(BigDecimal sueldoMensual) {
        // 1. Proyección Anual: 12 meses + 2 gratificaciones (Julio y Diciembre) = 14 sueldos
        BigDecimal proyeccionAnual = sueldoMensual.multiply(new BigDecimal("14"));

        // 2. Deducción Inafecta obligatoria de Ley (7 UIT)
        BigDecimal deduccion7UIT = VALOR_UIT.multiply(new BigDecimal("7"));
        BigDecimal rentaImponible = proyeccionAnual.subtract(deduccion7UIT);

        // Si no supera las 7 UIT al año, no paga Renta de 5ta. Retornamos cero exacto.
        if (rentaImponible.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // 3. CÁLCULO ESTRICTO POR TRAMOS (Ley de Impuesto a la Renta - Perú)
        BigDecimal impuestoAnual = BigDecimal.ZERO;
        BigDecimal restante = rentaImponible;

        BigDecimal uit5 = VALOR_UIT.multiply(new BigDecimal("5"));   // Tramo 1 límite
        BigDecimal uit15 = VALOR_UIT.multiply(new BigDecimal("15")); // Tramo 2 y 3 usan bloques de 15 UIT
        BigDecimal uit10 = VALOR_UIT.multiply(new BigDecimal("10")); // Tramo 4 usa bloque de 10 UIT

        // Tramo 1: Hasta 5 UIT (8%)
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = restante.min(uit5);
            impuestoAnual = impuestoAnual.add(baseTramo.multiply(new BigDecimal("0.08")));
            restante = restante.subtract(baseTramo);
        }

        // Tramo 2: Por el exceso de 5 hasta 20 UIT (Bloque de 15 UIT) (14%)
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = restante.min(uit15);
            impuestoAnual = impuestoAnual.add(baseTramo.multiply(new BigDecimal("0.14")));
            restante = restante.subtract(baseTramo);
        }

        // Tramo 3: Por el exceso de 20 hasta 35 UIT (Bloque de 15 UIT) (17%)
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = restante.min(uit15);
            impuestoAnual = impuestoAnual.add(baseTramo.multiply(new BigDecimal("0.17")));
            restante = restante.subtract(baseTramo);
        }

        // Tramo 4: Por el exceso de 35 hasta 45 UIT (Bloque de 10 UIT) (20%)
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = restante.min(uit10);
            impuestoAnual = impuestoAnual.add(baseTramo.multiply(new BigDecimal("0.20")));
            restante = restante.subtract(baseTramo);
        }

        // Tramo 5: Por el exceso de 45 UIT a más (30%)
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            impuestoAnual = impuestoAnual.add(restante.multiply(new BigDecimal("0.30")));
        }

        // 4. Fraccionamiento Mensual: Dividimos el impuesto anual entre 12 meses
        // (Nota: Para simplificar el prototipo usamos / 12, aunque SUNAT usa divisores variables en meses específicos)
        return impuestoAnual.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }
}