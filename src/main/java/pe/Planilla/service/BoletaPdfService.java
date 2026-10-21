package pe.Planilla.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.dto.LineaBoletaDTO;
import pe.Planilla.model.entity.BoletaPago;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.PlanillaCabecera;
import pe.Planilla.repository.BoletaPagoRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.PlanillaCabeceraRepository;
import pe.Planilla.service.calculo.CodigosPlame;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Boleta de pago en PDF con el formato de la boleta modelo de la empresa: cabecera con RUC y dirección, datos del
 * trabajador, tres columnas (ingresos, descuentos, aportes del empleador) con los conceptos fijos del modelo, totales,
 * neto a pagar, firmas y la referencia al D.S. 003-2010-TR. Se imprime por duplicado (trabajador y empleador).
 */
@Service
@RequiredArgsConstructor
public class BoletaPdfService {

    private static final String[] MESES = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO",
            "SETIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};
    private static final DecimalFormat MONTO = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font TITULO = new Font(Font.COURIER, 10, Font.BOLD);
    private static final Font NORMAL = new Font(Font.COURIER, 6.8f);
    private static final Font NEGRITA = new Font(Font.COURIER, 6.8f, Font.BOLD);
    private static final Font CHICA = new Font(Font.COURIER, 6f);

    private final BoletaPagoRepository boletaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PlanillaCabeceraRepository planillaRepository;
    private final PlanillaService planillaService;
    private final PlanillaProperties props;

    /** Un trabajador, por duplicado en una hoja (como la boleta modelo). */
    public byte[] generar(Integer idBoleta) {
        BoletaGeneradaDTO b = planillaService.obtenerBoleta(idBoleta);
        Empleado e = empleadoRepository.findById(b.getIdEmpleado()).orElseThrow();
        List<Object[]> pares = new ArrayList<>();
        pares.add(new Object[]{b, e});
        return construir(pares, true);
    }

    /** Todas las boletas del periodo en un solo PDF (una hoja por trabajador, por duplicado). */
    public byte[] generarPeriodo(String periodo) {
        List<BoletaGeneradaDTO> boletas = planillaService.listarBoletas(periodo);
        if (boletas.isEmpty()) {
            throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo + ".");
        }
        List<Object[]> pares = new ArrayList<>();
        for (BoletaGeneradaDTO b : boletas) {
            pares.add(new Object[]{b, empleadoRepository.findById(b.getIdEmpleado()).orElseThrow()});
        }
        return construir(pares, true);
    }

    public String nombreArchivo(BoletaGeneradaDTO b) {
        String[] p = b.getPeriodo().split("-");
        return "IP_BP_" + p[1] + " " + p[0] + "_" + b.getNombresEmpleado().toUpperCase().replaceAll("[^A-ZÁÉÍÓÚÑ ]", "").trim() + ".pdf";
    }

    // ---------------------------------------------------------------- armado

    private byte[] construir(List<Object[]> pares, boolean duplicado) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 22, 22, 14, 14);
            PdfWriter.getInstance(doc, out);
            doc.open();
            boolean primera = true;
            for (Object[] par : pares) {
                if (!primera) doc.newPage();
                primera = false;
                BoletaGeneradaDTO b = (BoletaGeneradaDTO) par[0];
                Empleado e = (Empleado) par[1];
                PlanillaCabecera cab = planillaRepository.findByPeriodo(b.getPeriodo()).orElse(null);
                doc.add(boleta(b, e, cab));
                if (duplicado) {
                    doc.add(separador());
                    doc.add(boleta(b, e, cab));
                }
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new RuntimeException("No se pudo generar el PDF: " + ex.getMessage());
        }
    }

    private Paragraph separador() {
        Paragraph p = par("- ".repeat(68) + "✂", CHICA);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(4);
        p.setSpacingAfter(4);
        return p;
    }

    private PdfPTable boleta(BoletaGeneradaDTO b, Empleado e, PlanillaCabecera cab) {
        PdfPTable marco = new PdfPTable(1);
        marco.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.BOX);
        celda.setPadding(4);

        celda.addElement(cabecera(b, e, cab));
        celda.addElement(linea());
        celda.addElement(cuerpo(b, e));
        celda.addElement(linea());
        celda.addElement(pie());
        marco.addCell(celda);
        return marco;
    }

    private Paragraph linea() {
        Paragraph p = par("=".repeat(138), CHICA);
        p.setLeading(6);
        return p;
    }

    private PdfPTable cabecera(BoletaGeneradaDTO b, Empleado e, PlanillaCabecera cab) {
        YearMonth ym = YearMonth.parse(b.getPeriodo());
        String periodoTexto = MESES[ym.getMonthValue() - 1] + " " + ym.getYear();
        LocalDate fechaPago = cab != null && cab.getFechaPago() != null ? cab.getFechaPago() : ym.atEndOfMonth();
        String fechaPagoTexto = fechaPago.getDayOfMonth() + " DE " + MESES[fechaPago.getMonthValue() - 1] + " " + fechaPago.getYear();

        PdfPTable t = new PdfPTable(new float[]{55, 45});
        t.setWidthPercentage(100);

        // Izquierda: empresa; derecha: título
        PdfPCell izq = sinBorde();
        izq.addElement(par("RUC: " + props.getRuc(), NEGRITA));
        izq.addElement(par("COD. DEL TRAB.  " + e.getDni(), NORMAL));
        izq.addElement(par(props.getDireccion(), NORMAL));
        t.addCell(izq);
        PdfPCell der = sinBorde();
        Paragraph titulo = par("B O L E T A   D E   P A G O", TITULO);
        titulo.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(titulo);
        Paragraph empresa = par(props.getRazonSocial(), NEGRITA);
        empresa.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(empresa);
        t.addCell(der);

        // Datos del trabajador en dos columnas, como la boleta modelo
        String pension = "ONP".equals(e.getSistemaPensionario()) || e.getSistemaPensionario() == null ? "O.N.P."
                : (e.getAfp() != null ? e.getAfp().getNombre() : "AFP") + ("MIXTA".equals(e.getTipoComisionAfp()) ? " (Com. Mixta)" : "");
        String[][] filas = {
                {"APELLIDOS Y NOMBRES:  " + e.getApellidos().toUpperCase() + " " + e.getNombres().toUpperCase(), "PERIODO: " + periodoTexto},
                {"DOCUMENTO DE IDENTIDAD:  " + e.getDni(), "NETO A PAGAR: S/. " + MONTO.format(b.getTotalNeto())},
                {"SISTEMA PENSIONARIO:  " + pension, "FECHA DE PAGO: " + fechaPagoTexto},
                {"Nº DE CUSPP:  " + (e.getCuspp() != null ? e.getCuspp() : ""), "REMUNERACION BRUTA: S/. " + MONTO.format(e.getSueldoBase())},
                {"CARGO:  " + (e.getCargo() != null ? e.getCargo() : "") + "   F.ING: " + FECHA.format(e.getFechaIngreso())
                        + "   FIN CONT: " + (e.getFechaCese() != null ? FECHA.format(e.getFechaCese()) : ""), "Nº CARNET ESSALUD:"},
                {"VACACIONES:  DEL          AL", "LUGAR: " + props.getLugarPago()}
        };
        for (String[] f : filas) {
            PdfPCell a = sinBorde();
            a.addElement(par(f[0], NORMAL));
            t.addCell(a);
            PdfPCell c = sinBorde();
            Paragraph p = par(f[1], f[1].startsWith("NETO") ? NEGRITA : NORMAL);
            p.setAlignment(Element.ALIGN_RIGHT);
            c.addElement(p);
            t.addCell(c);
        }
        return t;
    }

    /** Tres columnas con los conceptos fijos del modelo; lo que la boleta no tenga sale en 0.00. */
    private PdfPTable cuerpo(BoletaGeneradaDTO b, Empleado e) {
        Map<String, BigDecimal> ing = new LinkedHashMap<>();
        Map<String, BigDecimal> des = new LinkedHashMap<>();
        Map<String, BigDecimal> apo = new LinkedHashMap<>();
        String[] ingresos = {"SUELDO", "HORAS TRABAJADAS", "FERIADO TRABAJADO", "HORAS EXTRAS", "ASIGNACION FAMILIAR", "VACACIONES",
                "GRATIFICACIONES", "BONIF. EXTRAORDINARIA 9%", "SUBSIDIOS MATERNIDAD/INCAPACIDAD", "MOVILIDAD", "REFRIGERIO",
                "COMISIÓN", "CAPACITACIÓN", "BONO"};
        String[] descuentos = {"O.N.P.", "ADEL. BONIF. EXTRAORD. 9%", "RENTA QUINTA CATEG.", "DESC. FALTA O PERMISO", "DSCTO TARDANZAS",
                "APORTE OBLIGATORIO AFP", "PRIMA DE SEGURO AFP", "COMISION A.F.P. (%)", "E.P.S PACIFICO SALUD", "ADELANTO DE SUELDO", "DESC|PERMISOS"};
        String[] aportes = {"ESSALUD"};
        for (String k : ingresos) ing.put(k, BigDecimal.ZERO);
        for (String k : descuentos) des.put(k, BigDecimal.ZERO);
        for (String k : aportes) apo.put(k, BigDecimal.ZERO);

        // Descuento por falta = sueldo no pagado (30 - días pagados) x sueldo/30, informativo como en el modelo
        int dias = b.getDiasTrabajados() != null ? b.getDiasTrabajados() : 30;
        List<LineaBoletaDTO> otrosIngresos = new ArrayList<>();
        List<LineaBoletaDTO> otrosDescuentos = new ArrayList<>();
        for (LineaBoletaDTO l : b.getDetalle()) {
            String c = l.getCodigo() == null ? "" : l.getCodigo();
            String d = l.getDescripcion().toLowerCase();
            switch (l.getTipo()) {
                case "INGRESO" -> {
                    if (c.equals(CodigosPlame.BASICO)) sumar(ing, "SUELDO", l.getMonto());
                    else if (c.equals(CodigosPlame.ASIGNACION_FAMILIAR)) sumar(ing, "ASIGNACION FAMILIAR", l.getMonto());
                    else if (c.equals(CodigosPlame.HORAS_EXTRAS_25) || c.equals(CodigosPlame.HORAS_EXTRAS_35)) sumar(ing, "HORAS EXTRAS", l.getMonto());
                    else if (c.equals(CodigosPlame.VACACIONES)) sumar(ing, "VACACIONES", l.getMonto());
                    else if (c.equals(CodigosPlame.GRATIFICACION)) sumar(ing, "GRATIFICACIONES", l.getMonto());
                    else if (c.equals(CodigosPlame.BONIF_EXTRAORDINARIA)) sumar(ing, "BONIF. EXTRAORDINARIA 9%", l.getMonto());
                    else if (d.contains("comisi")) sumar(ing, "COMISIÓN", l.getMonto());
                    else if (d.contains("movilidad")) sumar(ing, "MOVILIDAD", l.getMonto());
                    else if (d.contains("refrigerio")) sumar(ing, "REFRIGERIO", l.getMonto());
                    else if (d.contains("capacit")) sumar(ing, "CAPACITACIÓN", l.getMonto());
                    else if (d.contains("bono")) sumar(ing, "BONO", l.getMonto());
                    else otrosIngresos.add(l);
                }
                case "DESCUENTO" -> {
                    if (c.equals(CodigosPlame.ONP)) sumar(des, "O.N.P.", l.getMonto());
                    else if (c.equals(CodigosPlame.RENTA_5TA)) sumar(des, "RENTA QUINTA CATEG.", l.getMonto());
                    else if (c.equals(CodigosPlame.AFP_APORTE)) sumar(des, "APORTE OBLIGATORIO AFP", l.getMonto());
                    else if (c.equals(CodigosPlame.AFP_PRIMA)) sumar(des, "PRIMA DE SEGURO AFP", l.getMonto());
                    else if (c.equals(CodigosPlame.AFP_COMISION)) sumar(des, "COMISION A.F.P. (%)", l.getMonto());
                    else if (d.contains("tardanza")) sumar(des, "DSCTO TARDANZAS", l.getMonto());
                    else if (d.contains("adelanto")) sumar(des, "ADELANTO DE SUELDO", l.getMonto());
                    else if (d.contains("permiso")) sumar(des, "DESC|PERMISOS", l.getMonto());
                    else otrosDescuentos.add(l);
                }
                case "INFORMATIVO" -> {
                    if (c.equals(CodigosPlame.ESSALUD)) sumar(apo, "ESSALUD", l.getMonto());
                }
                default -> { }
            }
        }
        for (LineaBoletaDTO l : otrosIngresos) ing.put(l.getDescripcion().toUpperCase(), l.getMonto());
        for (LineaBoletaDTO l : otrosDescuentos) des.put(l.getDescripcion().toUpperCase(), l.getMonto());

        PdfPTable t = new PdfPTable(new float[]{38, 36, 26});
        t.setWidthPercentage(100);
        t.addCell(encabezado("I N G R E S O S"));
        t.addCell(encabezado("D E S C U E N T O S"));
        t.addCell(encabezado("APORTES DEL EMPLEADOR"));

        PdfPCell cIng = sinBorde();
        cIng.addElement(par("(días trab.)  " + dias + "   HORAS TRABAJADAS: " + (dias * 8), CHICA));
        for (Map.Entry<String, BigDecimal> en : ing.entrySet()) cIng.addElement(fila(en.getKey(), en.getValue()));
        t.addCell(cIng);
        PdfPCell cDes = sinBorde();
        cDes.addElement(par(" ", CHICA));
        for (Map.Entry<String, BigDecimal> en : des.entrySet()) cDes.addElement(fila(en.getKey(), en.getValue()));
        t.addCell(cDes);
        PdfPCell cApo = sinBorde();
        cApo.addElement(par(" ", CHICA));
        for (Map.Entry<String, BigDecimal> en : apo.entrySet()) cApo.addElement(fila(en.getKey(), en.getValue()));
        if (b.getDepositoCts() != null && b.getDepositoCts().signum() > 0) cApo.addElement(fila("CTS A DEPOSITAR", b.getDepositoCts()));
        t.addCell(cApo);

        t.addCell(total("Total", b.getTotalIngresos()));
        t.addCell(total("Total", b.getTotalDescuentos()));
        t.addCell(total("Total", b.getEssaludEmpleador() != null ? b.getEssaludEmpleador() : BigDecimal.ZERO));
        return t;
    }

    private PdfPTable pie() {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        for (String firma : new String[]{"FIRMA TRABAJADOR", "FIRMA EMPLEADOR\n" + props.getRazonSocial()}) {
            PdfPCell c = sinBorde();
            Paragraph p = par("______________________________\n" + firma, NORMAL);
            p.setAlignment(Element.ALIGN_CENTER);
            c.addElement(p);
            t.addCell(c);
        }
        PdfPCell ds = sinBorde();
        ds.setColspan(2);
        Paragraph p = par("D E C R E T O   S U P R E M O   Nº 003 - 2010 - TR", NEGRITA);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(6);
        ds.addElement(p);
        t.addCell(ds);
        return t;
    }

    // ---------------------------------------------------------------- piezas

    /** Párrafo con interlineado ajustado (fuente + 1.6 pt) para que las dos copias quepan en una hoja. */
    private Paragraph par(String texto, Font fuente) {
        return new Paragraph(fuente.getSize() + 1.6f, texto, fuente);
    }

    private void sumar(Map<String, BigDecimal> m, String k, BigDecimal v) {
        m.merge(k, v, BigDecimal::add);
    }

    private PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(1);
        return c;
    }

    private PdfPCell encabezado(String texto) {
        PdfPCell c = sinBorde();
        Paragraph p = par(texto, NEGRITA);
        p.setAlignment(Element.ALIGN_CENTER);
        c.addElement(p);
        c.setPaddingBottom(3);
        return c;
    }

    private PdfPTable fila(String concepto, BigDecimal monto) {
        PdfPTable f = new PdfPTable(new float[]{70, 30});
        f.setWidthPercentage(100);
        PdfPCell a = sinBorde();
        a.addElement(par(concepto, NORMAL));
        f.addCell(a);
        PdfPCell b = sinBorde();
        Paragraph p = par(MONTO.format(monto), NORMAL);
        p.setAlignment(Element.ALIGN_RIGHT);
        b.addElement(p);
        f.addCell(b);
        return f;
    }

    private PdfPCell total(String etiqueta, BigDecimal monto) {
        PdfPCell c = sinBorde();
        c.setBorder(Rectangle.TOP);
        c.setPaddingTop(3);
        Paragraph p = par(etiqueta + "   " + MONTO.format(monto), NEGRITA);
        p.setAlignment(Element.ALIGN_RIGHT);
        c.addElement(p);
        return c;
    }
}
