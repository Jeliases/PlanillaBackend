package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.model.entity.BoletaPago;
import pe.Planilla.repository.BoletaPagoRepository;
import pe.Planilla.service.PlameService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlameServiceImpl implements PlameService {

    private final BoletaPagoRepository boletaRepository;

    @Override
    public String generarTramaRemuneraciones(String periodo) {
        // En un escenario real, buscaríamos por periodo: boletaRepository.findByPlanilla_Periodo(periodo)
        // Para este prototipo, traeremos todas y filtraremos por código.
        List<BoletaPago> boletas = boletaRepository.findAll().stream()
                .filter(b -> b.getPlanilla().getPeriodo().equals(periodo))
                .toList();

        if (boletas.isEmpty()) {
            throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo);
        }

        StringBuilder trama = new StringBuilder();

        for (BoletaPago boleta : boletas) {
            String tipoDoc = "01"; // 01 = DNI según tabla de SUNAT
            String numDoc = boleta.getEmpleado().getDni();

            // 1. Línea de Ingresos (Ej: 0101 - Sueldo Básico)
            // Estructura PLAME: TipoDoc|NumDoc|CodConcepto|Devengado|Pagado|
            trama.append(tipoDoc).append("|")
                    .append(numDoc).append("|")
                    .append("0101").append("|") // Sueldo
                    .append(boleta.getTotalIngresos()).append("|")
                    .append(boleta.getTotalIngresos()).append("|\r\n");

            // 2. Línea de Renta de 5ta (Código SUNAT: 0304)
            if (boleta.getRenta5taRetenida() != null && boleta.getRenta5taRetenida().doubleValue() > 0) {
                trama.append(tipoDoc).append("|")
                        .append(numDoc).append("|")
                        .append("0304").append("|") // Renta 5ta Categoría
                        .append(boleta.getRenta5taRetenida()).append("|")
                        .append(boleta.getRenta5taRetenida()).append("|\r\n");
            }

            // 3. Línea de ONP (Código SUNAT: 0701)
            // Calculamos cuánto fue de ONP restando la renta de 5ta de los descuentos totales (solo para el prototipo)
            if (!"Cuarta Categoría (RxH)".equals(boleta.getEmpleado().getRegimenLaboral().getNombre())) {
                java.math.BigDecimal otrosDescuentos = boleta.getTotalDescuentos().subtract(boleta.getRenta5taRetenida() != null ? boleta.getRenta5taRetenida() : java.math.BigDecimal.ZERO);
                if (otrosDescuentos.doubleValue() > 0) {
                    trama.append(tipoDoc).append("|")
                            .append(numDoc).append("|")
                            .append("0701").append("|") // ONP
                            .append(otrosDescuentos).append("|")
                            .append(otrosDescuentos).append("|\r\n");
                }
            }
        }

        return trama.toString();
    }
}