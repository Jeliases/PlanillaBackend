package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.dto.LineaBoletaDTO;
import pe.Planilla.service.BoletaPdfService;
import pe.Planilla.service.ConstanciaPdfService;
import pe.Planilla.service.CorreoService;
import pe.Planilla.service.ExportacionService;
import pe.Planilla.service.PlanillaService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Boletas en PDF, envío por correo, archivos de banco/AFPnet/resumen y el tablero con porcentajes. */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BoletaPdfService pdfService;
    private final ConstanciaPdfService constanciaService;
    private final CorreoService correoService;
    private final ExportacionService exportacionService;
    private final PlanillaService planillaService;

    // ------------------------------------------------------------------ PDF

    @GetMapping("/boletas/{idBoleta}/pdf")
    public ResponseEntity<byte[]> boletaPdf(@PathVariable Integer idBoleta) {
        BoletaGeneradaDTO b = planillaService.obtenerBoleta(idBoleta);
        return archivo(pdfService.generar(idBoleta), MediaType.APPLICATION_PDF, pdfService.nombreArchivo(b), false);
    }

    @GetMapping("/planillas/{periodo}/boletas.pdf")
    public ResponseEntity<byte[]> boletasPeriodoPdf(@PathVariable String periodo) {
        return archivo(pdfService.generarPeriodo(periodo), MediaType.APPLICATION_PDF, "boletas-" + periodo + ".pdf", true);
    }

    // Constancias para el trabajador (como las hojas "Certificado" de la contadora): sin idEmpleado salen todas en un PDF
    @GetMapping("/planillas/{periodo}/constancias/gratificacion.pdf")
    public ResponseEntity<byte[]> constanciaGratificacion(@PathVariable String periodo, @RequestParam(required = false) Integer idEmpleado) {
        return archivo(constanciaService.gratificacion(periodo, idEmpleado), MediaType.APPLICATION_PDF,
                "liquidacion-gratificacion-" + periodo + ".pdf", idEmpleado == null);
    }

    @GetMapping("/planillas/{periodo}/constancias/cts.pdf")
    public ResponseEntity<byte[]> constanciaCts(@PathVariable String periodo, @RequestParam(required = false) Integer idEmpleado) {
        return archivo(constanciaService.cts(periodo, idEmpleado), MediaType.APPLICATION_PDF,
                "liquidacion-cts-" + periodo + ".pdf", idEmpleado == null);
    }

    // Adelanto de la 1ra quincena en Excel (para el banco)
    @GetMapping("/planillas/{periodo}/quincena.xlsx")
    public ResponseEntity<byte[]> quincena(@PathVariable String periodo) {
        return archivo(exportacionService.quincena(periodo), XLSX, "quincena-" + periodo + ".xlsx", true);
    }

    // --------------------------------------------------------------- correo

    @GetMapping("/correo/estado")
    public ResponseEntity<Map<String, Object>> estadoCorreo() {
        return ResponseEntity.ok(Map.of("habilitado", correoService.estaHabilitado()));
    }

    @PostMapping("/planillas/{periodo}/enviar-boletas")
    public ResponseEntity<Map<String, Object>> enviarBoletas(@PathVariable String periodo,
                                                             @RequestParam(required = false) Integer idBoleta) {
        return ResponseEntity.ok(correoService.enviarPeriodo(periodo, idBoleta));
    }

    // ------------------------------------------------------------- exportes

    @GetMapping("/planillas/{periodo}/banco.txt")
    public ResponseEntity<byte[]> banco(@PathVariable String periodo, @RequestParam(required = false) String banco) {
        String contenido = exportacionService.pagoBanco(periodo, banco);
        String nombre = "pago-" + (banco != null && !banco.isBlank() ? banco.toLowerCase() + "-" : "") + periodo + ".txt";
        return archivo(contenido.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN, nombre, true);
    }

    @GetMapping("/planillas/{periodo}/afpnet.xlsx")
    public ResponseEntity<byte[]> afpnet(@PathVariable String periodo) {
        return archivo(exportacionService.afpnet(periodo), XLSX, "afpnet-" + periodo + ".xlsx", true);
    }

    @GetMapping("/planillas/{periodo}/resumen.xlsx")
    public ResponseEntity<byte[]> resumen(@PathVariable String periodo) {
        return archivo(exportacionService.resumenPlanilla(periodo), XLSX, "planilla-" + periodo + ".xlsx", true);
    }

    // -------------------------------------------------------------- tablero

    /** Cifras del periodo con porcentajes sobre el total de ingresos (para el dashboard que pidió la empresa). */
    @GetMapping("/planillas/{periodo}/tablero")
    public ResponseEntity<Map<String, Object>> tablero(@PathVariable String periodo) {
        List<BoletaGeneradaDTO> boletas = planillaService.listarBoletas(periodo);
        BigDecimal ingresos = BigDecimal.ZERO, descuentos = BigDecimal.ZERO, neto = BigDecimal.ZERO, essalud = BigDecimal.ZERO, cts = BigDecimal.ZERO;
        Map<String, BigDecimal> porConcepto = new LinkedHashMap<>();
        Map<String, BigDecimal> porRegimen = new TreeMap<>();
        Map<String, Integer> trabajadoresPorRegimen = new TreeMap<>();
        Map<String, BigDecimal> porPension = new TreeMap<>();
        for (BoletaGeneradaDTO b : boletas) {
            ingresos = ingresos.add(b.getTotalIngresos());
            descuentos = descuentos.add(b.getTotalDescuentos());
            neto = neto.add(b.getTotalNeto());
            if (b.getEssaludEmpleador() != null) essalud = essalud.add(b.getEssaludEmpleador());
            if (b.getDepositoCts() != null) cts = cts.add(b.getDepositoCts());
            porRegimen.merge(b.getRegimen(), b.getTotalIngresos(), BigDecimal::add);
            trabajadoresPorRegimen.merge(b.getRegimen(), 1, Integer::sum);
            for (LineaBoletaDTO l : b.getDetalle()) {
                String clave = l.getTipo() + "|" + l.getDescripcion().replaceAll("\\s*\\(.*\\)", "");
                porConcepto.merge(clave, l.getMonto(), BigDecimal::add);
                if ("DESCUENTO".equals(l.getTipo()) && l.getCodigo() != null && l.getCodigo().startsWith("06")) {
                    String sistema = l.getCodigo().equals("0607") ? "ONP" : (l.getCodigo().equals("0605") ? "Renta 5ta" : "AFP");
                    porPension.merge(sistema, l.getMonto(), BigDecimal::add);
                }
            }
        }
        BigDecimal costoEmpresa = ingresos.add(essalud).add(cts);
        List<Map<String, Object>> conceptos = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : porConcepto.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", e.getKey().substring(0, e.getKey().indexOf('|')));
            m.put("concepto", e.getKey().substring(e.getKey().indexOf('|') + 1));
            m.put("monto", e.getValue());
            m.put("porcentaje", pct(e.getValue(), ingresos));
            conceptos.add(m);
        }
        conceptos.sort((a, b) -> ((BigDecimal) b.get("monto")).compareTo((BigDecimal) a.get("monto")));

        List<Map<String, Object>> regimenes = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : porRegimen.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("regimen", e.getKey());
            m.put("trabajadores", trabajadoresPorRegimen.get(e.getKey()));
            m.put("ingresos", e.getValue());
            m.put("porcentaje", pct(e.getValue(), ingresos));
            regimenes.add(m);
        }
        List<Map<String, Object>> pensiones = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : porPension.entrySet()) {
            pensiones.add(Map.of("sistema", e.getKey(), "monto", e.getValue(), "porcentaje", pct(e.getValue(), ingresos)));
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("periodo", periodo);
        r.put("trabajadores", boletas.size());
        r.put("totalIngresos", ingresos);
        r.put("totalDescuentos", descuentos);
        r.put("porcentajeDescuentos", pct(descuentos, ingresos));
        r.put("totalNeto", neto);
        r.put("porcentajeNeto", pct(neto, ingresos));
        r.put("essaludEmpleador", essalud);
        r.put("ctsADepositar", cts);
        r.put("costoEmpresa", costoEmpresa);
        r.put("porcentajeCargaEmpleador", pct(essalud.add(cts), ingresos));
        r.put("promedioNeto", boletas.isEmpty() ? BigDecimal.ZERO : neto.divide(new BigDecimal(boletas.size()), 2, RoundingMode.HALF_UP));
        r.put("conceptos", conceptos);
        r.put("regimenes", regimenes);
        r.put("pensiones", pensiones);
        return ResponseEntity.ok(r);
    }

    private BigDecimal pct(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return parte.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
    }

    private ResponseEntity<byte[]> archivo(byte[] datos, MediaType tipo, String nombre, boolean descargar) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(tipo);
        if (descargar) h.setContentDispositionFormData("attachment", nombre);
        else h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"");
        return new ResponseEntity<>(datos, h, HttpStatus.OK);
    }
}
