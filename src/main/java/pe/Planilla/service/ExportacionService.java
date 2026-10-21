package pe.Planilla.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.dto.LineaBoletaDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.service.calculo.CodigosPlame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Archivos que salen de la planilla cerrada:
 *  - Pago masivo al banco (.txt): una línea por trabajador con cuenta y neto. FORMATO PROVISIONAL: el banco (Interbank/BBVA)
 *    define el layout exacto; cuando la contadora adjunte el ejemplo se ajusta aquí.
 *  - AFPnet (.xlsx): un Excel con los datos que AFPnet importa (CUSPP, documento, nombres, remuneración asegurable y
 *    los tres aportes). AFPnet también admite digitar los montos; el layout oficial se valida con su plantilla.
 *  - Resumen de planilla (.xlsx): una fila por trabajador con todos los conceptos en columnas.
 */
@Service
@RequiredArgsConstructor
public class ExportacionService {

    private final PlanillaService planillaService;
    private final EmpleadoRepository empleadoRepository;
    private final PlanillaProperties props;

    // ------------------------------------------------------------------ banco

    /**
     * Texto separado por "|" (formato provisional): tipoDoc|numDoc|apellidos y nombres|banco|cuenta|moneda|monto.
     * Solo entran trabajadores con cuenta bancaria registrada; los demás se listan al final como aviso.
     */
    @Transactional(readOnly = true)
    public String pagoBanco(String periodo, String banco) {
        List<BoletaGeneradaDTO> boletas = planillaService.listarBoletas(periodo);
        if (boletas.isEmpty()) throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo + ".");

        StringBuilder sb = new StringBuilder();
        List<String> sinCuenta = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (BoletaGeneradaDTO b : boletas) {
            Empleado e = empleadoRepository.findById(b.getIdEmpleado()).orElseThrow();
            if (banco != null && !banco.isBlank() && (e.getBanco() == null || !e.getBanco().equalsIgnoreCase(banco.trim()))) continue;
            if (e.getCuentaBancaria() == null || e.getCuentaBancaria().isBlank()) {
                sinCuenta.add(b.getNombresEmpleado());
                continue;
            }
            if (b.getTotalNeto().signum() <= 0) continue;
            n++;
            total = total.add(b.getTotalNeto());
            sb.append(tipoDoc(e)).append('|').append(e.getDni()).append('|')
                    .append(e.getApellidos().toUpperCase()).append(' ').append(e.getNombres().toUpperCase()).append('|')
                    .append(e.getBanco() != null ? e.getBanco().toUpperCase() : "").append('|')
                    .append(e.getCuentaBancaria()).append('|')
                    .append("PEN").append('|')
                    .append(b.getTotalNeto().setScale(2, RoundingMode.HALF_UP)).append("\r\n");
        }
        sb.append("TOTAL|").append(n).append("|").append(total.setScale(2, RoundingMode.HALF_UP)).append("\r\n");
        if (!sinCuenta.isEmpty()) {
            sb.append("# SIN CUENTA BANCARIA (no incluidos): ").append(String.join(", ", sinCuenta)).append("\r\n");
        }
        return sb.toString();
    }

    // ----------------------------------------------------------------- AFPnet

    @Transactional(readOnly = true)
    public byte[] afpnet(String periodo) {
        List<BoletaGeneradaDTO> boletas = planillaService.listarBoletas(periodo);
        if (boletas.isEmpty()) throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo + ".");

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("AFPnet " + periodo);
            String[] cab = {"AFP", "CUSPP", "Tipo doc", "N° documento", "Apellido paterno y materno", "Nombres",
                    "Remuneración asegurable", "Aporte obligatorio", "Comisión", "Prima de seguro", "Total AFP", "Tipo comisión"};
            Row r0 = hoja.createRow(0);
            CellStyle negrita = libro.createCellStyle();
            Font f = libro.createFont();
            f.setBold(true);
            negrita.setFont(f);
            for (int i = 0; i < cab.length; i++) {
                Cell c = r0.createCell(i);
                c.setCellValue(cab[i]);
                c.setCellStyle(negrita);
                hoja.setColumnWidth(i, 22 * 256);
            }
            int fila = 1;
            for (BoletaGeneradaDTO b : boletas) {
                Empleado e = empleadoRepository.findById(b.getIdEmpleado()).orElseThrow();
                if (!"AFP".equals(e.getSistemaPensionario())) continue;
                BigDecimal aporte = monto(b, CodigosPlame.AFP_APORTE), comision = monto(b, CodigosPlame.AFP_COMISION), prima = monto(b, CodigosPlame.AFP_PRIMA);
                // Remuneración asegurable = base del aporte obligatorio (10%)
                BigDecimal asegurable = e.getAfp() != null && e.getAfp().getAporteObligatorio().signum() > 0
                        ? aporte.divide(e.getAfp().getAporteObligatorio(), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                Row r = hoja.createRow(fila++);
                int c = 0;
                r.createCell(c++).setCellValue(e.getAfp() != null ? e.getAfp().getNombre() : "");
                r.createCell(c++).setCellValue(e.getCuspp() != null ? e.getCuspp() : "");
                r.createCell(c++).setCellValue(tipoDoc(e));
                r.createCell(c++).setCellValue(e.getDni());
                r.createCell(c++).setCellValue(e.getApellidos());
                r.createCell(c++).setCellValue(e.getNombres());
                r.createCell(c++).setCellValue(asegurable.doubleValue());
                r.createCell(c++).setCellValue(aporte.doubleValue());
                r.createCell(c++).setCellValue(comision.doubleValue());
                r.createCell(c++).setCellValue(prima.doubleValue());
                r.createCell(c++).setCellValue(aporte.add(comision).add(prima).doubleValue());
                r.createCell(c).setCellValue(e.getTipoComisionAfp() != null ? e.getTipoComisionAfp() : "FLUJO");
            }
            libro.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo generar el archivo AFPnet: " + ex.getMessage());
        }
    }

    // --------------------------------------------------------------- quincena

    /** Adelanto de la 1ra quincena: una fila por trabajador con su cuenta y el monto (para pagarlo por el banco). */
    @Transactional(readOnly = true)
    public byte[] quincena(String periodo) {
        List<PlanillaService.Quincena> filas = planillaService.calcularQuincena(periodo);
        if (filas.isEmpty()) throw new RuntimeException("No hay trabajadores para la quincena de " + periodo + ".");

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Quincena " + periodo);
            String[] cab = {"Tipo doc", "N° documento", "Apellidos y nombres", "Banco", "Cuenta", "Básico", "Asig. familiar",
                    "Pensión (1/2)", "Renta 5ta (1/2)", "Adelanto", "Registrado en planilla"};
            Row r0 = hoja.createRow(0);
            CellStyle negrita = libro.createCellStyle();
            Font f = libro.createFont();
            f.setBold(true);
            negrita.setFont(f);
            for (int i = 0; i < cab.length; i++) {
                Cell c = r0.createCell(i);
                c.setCellValue(cab[i]);
                c.setCellStyle(negrita);
                hoja.setColumnWidth(i, (i == 2 ? 36 : 18) * 256);
            }
            int fila = 1;
            BigDecimal total = BigDecimal.ZERO;
            for (PlanillaService.Quincena q : filas) {
                Row r = hoja.createRow(fila++);
                int c = 0;
                r.createCell(c++).setCellValue(q.tipoDocumento());
                r.createCell(c++).setCellValue(q.documento());
                r.createCell(c++).setCellValue(q.empleado());
                r.createCell(c++).setCellValue(q.banco() != null ? q.banco() : "");
                r.createCell(c++).setCellValue(q.cuenta() != null ? q.cuenta() : "");
                r.createCell(c++).setCellValue(q.basico().doubleValue());
                r.createCell(c++).setCellValue(q.asignacionFamiliar().doubleValue());
                r.createCell(c++).setCellValue(q.pension().doubleValue());
                r.createCell(c++).setCellValue(q.renta5ta().doubleValue());
                r.createCell(c++).setCellValue(q.adelanto().doubleValue());
                r.createCell(c).setCellValue(q.registrado() ? "SI" : "NO");
                total = total.add(q.adelanto());
            }
            Row rt = hoja.createRow(fila);
            Cell ct = rt.createCell(2);
            ct.setCellValue("TOTAL");
            ct.setCellStyle(negrita);
            Cell cm = rt.createCell(9);
            cm.setCellValue(total.doubleValue());
            cm.setCellStyle(negrita);
            libro.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo generar el Excel de la quincena: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- resumen

    @Transactional(readOnly = true)
    public byte[] resumenPlanilla(String periodo) {
        List<BoletaGeneradaDTO> boletas = planillaService.listarBoletas(periodo);
        if (boletas.isEmpty()) throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo + ".");

        // Columnas dinámicas: todos los conceptos que aparecen en alguna boleta, en orden de aparición
        LinkedHashMap<String, String> conceptos = new LinkedHashMap<>();
        for (BoletaGeneradaDTO b : boletas) {
            for (LineaBoletaDTO l : b.getDetalle()) {
                conceptos.putIfAbsent(l.getTipo() + "|" + l.getDescripcion().replaceAll("\\s*\\(.*\\)", ""), l.getTipo());
            }
        }
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Planilla " + periodo);
            CellStyle negrita = libro.createCellStyle();
            Font f = libro.createFont();
            f.setBold(true);
            negrita.setFont(f);
            CellStyle dinero = libro.createCellStyle();
            dinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            List<String> cab = new ArrayList<>(List.of("Documento", "Apellidos y nombres", "Cargo", "Régimen", "Pensión", "Días"));
            for (String k : conceptos.keySet()) cab.add(k.substring(k.indexOf('|') + 1));
            cab.addAll(List.of("Total ingresos", "Total descuentos", "Neto a pagar", "EsSalud empleador", "CTS a depositar", "Costo empresa"));
            Row r0 = hoja.createRow(0);
            for (int i = 0; i < cab.size(); i++) {
                Cell c = r0.createCell(i);
                c.setCellValue(cab.get(i));
                c.setCellStyle(negrita);
                hoja.setColumnWidth(i, Math.max(14, Math.min(40, cab.get(i).length() + 4)) * 256);
            }
            int fila = 1;
            BigDecimal[] totales = new BigDecimal[cab.size()];
            Arrays.fill(totales, BigDecimal.ZERO);
            for (BoletaGeneradaDTO b : boletas) {
                Empleado e = empleadoRepository.findById(b.getIdEmpleado()).orElseThrow();
                Row r = hoja.createRow(fila++);
                int c = 0;
                r.createCell(c++).setCellValue(tipoDoc(e) + " " + e.getDni());
                r.createCell(c++).setCellValue(e.getApellidos() + " " + e.getNombres());
                r.createCell(c++).setCellValue(e.getCargo() != null ? e.getCargo() : "");
                r.createCell(c++).setCellValue(b.getRegimen());
                r.createCell(c++).setCellValue("AFP".equals(e.getSistemaPensionario()) ? "AFP " + (e.getAfp() != null ? e.getAfp().getNombre() : "") : "ONP");
                r.createCell(c++).setCellValue(b.getDiasTrabajados() != null ? b.getDiasTrabajados() : 0);
                for (String k : conceptos.keySet()) {
                    BigDecimal v = b.getDetalle().stream()
                            .filter(l -> k.equals(l.getTipo() + "|" + l.getDescripcion().replaceAll("\\s*\\(.*\\)", "")))
                            .map(LineaBoletaDTO::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
                    celdaDinero(r, c, v, dinero);
                    totales[c] = totales[c].add(v);
                    c++;
                }
                BigDecimal essalud = b.getEssaludEmpleador() != null ? b.getEssaludEmpleador() : BigDecimal.ZERO;
                BigDecimal cts = b.getDepositoCts() != null ? b.getDepositoCts() : BigDecimal.ZERO;
                BigDecimal[] fin = {b.getTotalIngresos(), b.getTotalDescuentos(), b.getTotalNeto(), essalud, cts, b.getTotalIngresos().add(essalud).add(cts)};
                for (BigDecimal v : fin) {
                    celdaDinero(r, c, v, dinero);
                    totales[c] = totales[c].add(v);
                    c++;
                }
            }
            Row rt = hoja.createRow(fila);
            Cell ct = rt.createCell(1);
            ct.setCellValue("TOTALES (" + boletas.size() + " trabajadores)");
            ct.setCellStyle(negrita);
            for (int i = 6; i < cab.size(); i++) {
                Cell c = rt.createCell(i);
                c.setCellValue(totales[i].doubleValue());
                c.setCellStyle(dinero);
            }
            hoja.createFreezePane(2, 1);
            libro.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo generar el resumen: " + ex.getMessage());
        }
    }

    // ----------------------------------------------------------------- ayudas

    private String tipoDoc(Empleado e) {
        return e.getTipoDocumento() != null ? e.getTipoDocumento() : "DNI";
    }

    private BigDecimal monto(BoletaGeneradaDTO b, String codigo) {
        return b.getDetalle().stream().filter(l -> codigo.equals(l.getCodigo())).map(LineaBoletaDTO::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void celdaDinero(Row r, int c, BigDecimal v, CellStyle estilo) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v.doubleValue());
        cell.setCellStyle(estilo);
    }
}
