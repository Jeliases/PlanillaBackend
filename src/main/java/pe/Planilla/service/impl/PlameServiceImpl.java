package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.model.TipoDocumento;
import pe.Planilla.model.entity.BoletaDetalle;
import pe.Planilla.model.entity.BoletaPago;
import pe.Planilla.repository.BoletaPagoRepository;
import pe.Planilla.service.PlameService;
import pe.Planilla.service.calculo.CodigosPlame;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlameServiceImpl implements PlameService {

    private final BoletaPagoRepository boletaRepository;

    /**
     * Archivo de remuneraciones (.rem) para importar en el PDT PLAME. Una fila por concepto de cada boleta:
     * TipoDoc|NumDoc|CodConcepto|MontoDevengado|MontoPagado|
     *
     * Se declaran los ingresos, los descuentos tributarios/previsionales (códigos 06xx) y el aporte de EsSalud.
     * Las tardanzas, adelantos y otros descuentos internos no se declaran.
     */
    @Override
    @Transactional(readOnly = true)
    public String generarTramaRemuneraciones(String periodo) {
        List<BoletaPago> boletas = boletaRepository.findByPlanilla_Periodo(periodo);

        if (boletas.isEmpty()) {
            throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo);
        }

        StringBuilder trama = new StringBuilder();
        for (BoletaPago boleta : boletas) {
            // Código SUNAT del tipo de documento del trabajador (01 DNI, 04 CE, 07 Pasaporte, 23 PTP...)
            String tipoDoc = TipoDocumento.desdeTexto(boleta.getEmpleado().getTipoDocumento()).getCodigoSunat();
            String numDoc = boleta.getEmpleado().getDni();

            for (BoletaDetalle linea : boleta.getDetalle()) {
                if (!seDeclara(linea)) continue;
                trama.append(tipoDoc).append("|")
                        .append(numDoc).append("|")
                        .append(linea.getCodigo()).append("|")
                        .append(linea.getMonto()).append("|")
                        .append(linea.getMonto()).append("|\r\n");
            }
        }

        return trama.toString();
    }

    private boolean seDeclara(BoletaDetalle linea) {
        if (linea.getCodigo() == null) return false;
        return switch (linea.getTipo()) {
            case BoletaDetalle.INGRESO -> true;
            case BoletaDetalle.DESCUENTO -> linea.getCodigo().startsWith("06");
            case BoletaDetalle.INFORMATIVO -> CodigosPlame.ESSALUD.equals(linea.getCodigo());
            default -> false;
        };
    }
}
