package pe.Planilla.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.PlanillaCabeceraRepository;
import pe.Planilla.service.PlanillaService.DetalleCts;
import pe.Planilla.service.PlanillaService.DetalleGratificacion;
import pe.Planilla.service.calculo.CodigosPlame;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Constancias que la contadora entrega a cada trabajador (copiadas de sus hojas "Certificado Gratif" y "Certificado de CTS"):
 * liquidación de gratificaciones (julio y diciembre) y liquidación del depósito semestral de CTS (mayo y noviembre).
 * Una hoja por trabajador, con el desglose de la remuneración computable y el cálculo.
 */
@Service
@RequiredArgsConstructor
public class ConstanciaPdfService {

    private static final DecimalFormat MONTO = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font TITULO = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 9);
    private static final Font NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font CHICA = new Font(Font.HELVETICA, 7.5f);

    private final PlanillaService planillaService;
    private final EmpleadoRepository empleadoRepository;
    private final PlanillaCabeceraRepository planillaRepository;
    private final PlanillaProperties props;

    /** Liquidación de gratificaciones del periodo (julio o diciembre): un trabajador o todos los que la cobraron. */
    public byte[] gratificacion(String periodo, Integer idEmpleado) {
        List<Integer> ids = idEmpleado != null ? List.of(idEmpleado) : conGratificacion(periodo);
        return construir(doc -> {
            boolean primera = true;
            for (Integer id : ids) {
                DetalleGratificacion d = planillaService.detalleGratificacion(id, periodo);
                if (d.total().signum() <= 0 && idEmpleado == null) continue;
                if (!primera) doc.newPage();
                primera = false;
                paginaGratificacion(doc, empleadoRepository.findById(id).orElseThrow(), d);
            }
        });
    }

    /** Liquidación del depósito semestral de CTS del periodo (mayo o noviembre). */
    public byte[] cts(String periodo, Integer idEmpleado) {
        List<Integer> ids = idEmpleado != null ? List.of(idEmpleado) : conCts(periodo);
        return construir(doc -> {
            boolean primera = true;
            for (Integer id : ids) {
                DetalleCts d = planillaService.detalleCts(id, periodo);
                if (d.monto().signum() <= 0 && idEmpleado == null) continue;
                if (!primera) doc.newPage();
                primera = false;
                paginaCts(doc, empleadoRepository.findById(id).orElseThrow(), d);
            }
        });
    }

    // ------------------------------------------------------------ páginas

    private void paginaGratificacion(Document doc, Empleado e, DetalleGratificacion d) throws DocumentException {
        YearMonth ym = YearMonth.parse(d.periodo());
        String semestre = ym.getMonthValue() == 7 ? "ENERO A JUNIO" : "JULIO A DICIEMBRE";
        String fiesta = ym.getMonthValue() == 7 ? "Fiestas Patrias" : "Navidad";

        doc.add(centrado(props.getRazonSocial().toUpperCase(), SUBTITULO));
        doc.add(centrado("LIQUIDACIÓN DE GRATIFICACIONES - " + semestre + " " + ym.getYear(), TITULO));
        doc.add(espacio(8));
        doc.add(datos(e, d.desde(), d.hasta(), d.meses() + " meses", e.getBanco()));
        doc.add(espacio(8));
        doc.add(parrafo(props.getRazonSocial() + ", con RUC Nº " + props.getRuc() + ", domiciliada en " + props.getDireccion()
                + ", representada por su gerente general " + representante() + ", otorga a " + nombre(e) + " (" + documento(e)
                + ") la presente constancia de depósito de su gratificación de " + fiesta + " " + ym.getYear()
                + ", depositada en su cuenta de haberes" + banco(e.getBanco()) + ", por los siguientes montos y periodos:"));
        doc.add(espacio(6));

        doc.add(parrafo("1.  Período que se liquida", NEGRITA));
        doc.add(parrafo("Del " + d.desde().format(FECHA) + " al " + d.hasta().format(FECHA) + " (" + d.meses() + " meses completos)"));
        doc.add(espacio(6));

        doc.add(parrafo("2.  Remuneración computable", NEGRITA));
        PdfPTable rc = tabla();
        fila(rc, "Básico" + jornada(e), d.basico());
        fila(rc, "Asignación familiar", d.asignacionFamiliar());
        fila(rc, "Promedio de horas extras y comisiones del semestre (suma / 6)", d.promedioVariable());
        total(rc, "Total", d.computable());
        doc.add(rc);
        doc.add(espacio(6));

        doc.add(parrafo("3.  Cálculo (" + e.getRegimenLaboral().getNombre() + ")", NEGRITA));
        PdfPTable calc = tabla();
        String factor = d.factor().compareTo(BigDecimal.ONE) == 0 ? "" : " x " + d.factor().stripTrailingZeros().toPlainString();
        fila(calc, "Por los meses completos: S/ " + MONTO.format(d.computable()) + " / 6 x " + d.meses() + " meses" + factor, d.bruta());
        if (d.faltas() > 0) fila(calc, "Descuento por faltas injustificadas (" + d.faltas() + " x 1/180)", d.descuentoFaltas().negate());
        fila(calc, "Gratificación", d.neta());
        fila(calc, "Bonificación extraordinaria 9% (Ley 30334)", d.bonificacion());
        total(calc, "TOTAL A DEPOSITAR", d.total());
        doc.add(calc);
        doc.add(espacio(4));
        doc.add(parrafo("La gratificación y su bonificación extraordinaria no están afectas a aportes ni descuentos de pensiones (Ley 30334); "
                + "sí se consideran para la Renta de 5ta categoría.", CHICA));
        doc.add(espacio(10));
        doc.add(parrafo("Fecha: " + fechaConstancia(d.periodo()).format(FECHA)));
        doc.add(firmas(e));
    }

    private void paginaCts(Document doc, Empleado e, DetalleCts d) throws DocumentException {
        YearMonth ym = YearMonth.parse(d.periodo());
        String tiempo = d.meses() + " meses" + (d.dias() > 0 ? " y " + d.dias() + " días" : "");

        doc.add(centrado(props.getRazonSocial().toUpperCase(), SUBTITULO));
        doc.add(centrado("LIQUIDACIÓN DE DEPÓSITO SEMESTRAL DE CTS - " + (ym.getMonthValue() == 5 ? "MAYO" : "NOVIEMBRE") + " " + ym.getYear(), TITULO));
        doc.add(espacio(8));
        doc.add(datos(e, d.desde(), d.hasta(), tiempo, e.getBancoCts()));
        doc.add(espacio(8));
        doc.add(parrafo(props.getRazonSocial() + ", con RUC Nº " + props.getRuc() + ", domiciliada en " + props.getDireccion()
                + ", representada por su gerente general " + representante()
                + ", en aplicación del artículo 24 del TUO del Decreto Legislativo 650, Ley de Compensación por Tiempo de Servicios, "
                + "aprobado mediante D.S. Nº 001-97-TR, otorga a " + nombre(e) + " (" + documento(e)
                + ") la presente constancia de depósito de su Compensación por Tiempo de Servicios, depositada en su cuenta de CTS"
                + banco(e.getBancoCts()) + ", por los siguientes montos y periodos:"));
        doc.add(espacio(6));

        doc.add(parrafo("1.  Período que se liquida", NEGRITA));
        doc.add(parrafo("Del " + d.desde().format(FECHA) + " al " + d.hasta().format(FECHA) + " (" + tiempo + ")"));
        doc.add(espacio(6));

        doc.add(parrafo("2.  Remuneración computable", NEGRITA));
        PdfPTable rc = tabla();
        fila(rc, "Básico" + jornada(e), d.basico());
        fila(rc, "Asignación familiar", d.asignacionFamiliar());
        fila(rc, "Promedio de horas extras y comisiones del semestre (suma / 6)", d.promedioVariable());
        fila(rc, "1/6 de la gratificación (S/ " + MONTO.format(d.gratificacionBase()) + " / 6)", d.sextoGratificacion());
        total(rc, "Total", d.computable());
        doc.add(rc);
        doc.add(espacio(6));

        doc.add(parrafo("3.  Cálculo (" + e.getRegimenLaboral().getNombre() + ")", NEGRITA));
        PdfPTable calc = tabla();
        BigDecimal base = d.computable().multiply(d.factor());
        BigDecimal porMeses = base.multiply(new BigDecimal(d.meses())).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        BigDecimal porDias = base.multiply(new BigDecimal(d.dias())).divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);
        BigDecimal porFaltas = base.multiply(new BigDecimal(d.faltas())).divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);
        String factor = d.factor().compareTo(BigDecimal.ONE) == 0 ? "" : " x " + d.factor().stripTrailingZeros().toPlainString();
        fila(calc, "Por los meses completos: S/ " + MONTO.format(d.computable()) + " / 12 x " + d.meses() + " meses" + factor, porMeses);
        fila(calc, "Por los días: S/ " + MONTO.format(d.computable()) + " / 360 x " + d.dias() + " días" + factor, porDias);
        if (d.faltas() > 0) fila(calc, "Descuento por faltas injustificadas (" + d.faltas() + " días / 360)", porFaltas.negate());
        BigDecimal ajuste = d.monto().subtract(porMeses.add(porDias).subtract(porFaltas));
        if (ajuste.signum() != 0) fila(calc, "Ajuste por redondeo", ajuste);
        fila(calc, "Intereses", BigDecimal.ZERO);
        total(calc, "TOTAL DEPOSITADO", d.monto());
        doc.add(calc);
        doc.add(espacio(10));
        doc.add(parrafo("Monto depositado: S/ " + MONTO.format(d.monto()) + "          Fecha: " + fechaConstancia(d.periodo()).format(FECHA)));
        doc.add(firmas(e));
    }

    // ------------------------------------------------------------ piezas

    private PdfPTable datos(Empleado e, LocalDate desde, LocalDate hasta, String tiempo, String banco) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{22, 78});
        campo(t, "Trabajador:", nombre(e));
        campo(t, "Documento:", documento(e));
        campo(t, "Cargo / área:", (e.getCargo() != null ? e.getCargo() : "") + (e.getArea() != null ? " - " + e.getArea() : ""));
        campo(t, "Fecha de ingreso:", e.getFechaIngreso() != null ? e.getFechaIngreso().format(FECHA) : "");
        campo(t, "Banco:", banco != null ? banco : "");
        campo(t, "Periodo:", "Del " + desde.format(FECHA) + " al " + hasta.format(FECHA) + " (" + tiempo + ")");
        campo(t, "Empleador:", props.getRazonSocial() + " - RUC " + props.getRuc());
        return t;
    }

    private void campo(PdfPTable t, String etiqueta, String valor) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, NEGRITA));
        PdfPCell b = new PdfPCell(new Phrase(valor, NORMAL));
        a.setBorder(Rectangle.NO_BORDER);
        b.setBorder(Rectangle.NO_BORDER);
        a.setPaddingBottom(3);
        b.setPaddingBottom(3);
        t.addCell(a);
        t.addCell(b);
    }

    private PdfPTable tabla() throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{78, 22});
        return t;
    }

    private void fila(PdfPTable t, String concepto, BigDecimal monto) {
        PdfPCell a = new PdfPCell(new Phrase(concepto, NORMAL));
        PdfPCell b = new PdfPCell(new Phrase("S/ " + MONTO.format(monto), NORMAL));
        a.setBorder(Rectangle.NO_BORDER);
        b.setBorder(Rectangle.NO_BORDER);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }

    private void total(PdfPTable t, String etiqueta, BigDecimal monto) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, NEGRITA));
        PdfPCell b = new PdfPCell(new Phrase("S/ " + MONTO.format(monto), NEGRITA));
        a.setBorder(Rectangle.TOP);
        b.setBorder(Rectangle.TOP);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }

    private PdfPTable firmas(Empleado e) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(50);
        for (String texto : new String[]{"______________________________\n" + nombre(e) + "\n" + documento(e) + "\nTrabajador",
                "______________________________\n" + props.getRazonSocial() + "\nEmpleador"}) {
            PdfPCell c = new PdfPCell(new Phrase(texto, NORMAL));
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(c);
        }
        return t;
    }

    private Paragraph centrado(String texto, Font f) {
        Paragraph p = new Paragraph(texto, f);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private Paragraph parrafo(String texto) {
        return parrafo(texto, NORMAL);
    }

    private Paragraph parrafo(String texto, Font f) {
        Paragraph p = new Paragraph(texto, f);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(12);
        return p;
    }

    private Paragraph espacio(float alto) {
        Paragraph p = new Paragraph(" ", NORMAL);
        p.setLeading(alto);
        return p;
    }

    private String representante() {
        String rep = props.getRepresentanteLegal();
        if (rep == null || rep.isBlank()) return "______________________________";
        String doc = props.getRepresentanteDocumento();
        return rep + (doc != null && !doc.isBlank() ? ", con DNI " + doc : "");
    }

    private String nombre(Empleado e) {
        return (e.getApellidos() + " " + e.getNombres()).toUpperCase();
    }

    private String documento(Empleado e) {
        return (e.getTipoDocumento() != null ? e.getTipoDocumento() : "DNI") + " " + e.getDni();
    }

    private String banco(String banco) {
        return banco != null && !banco.isBlank() ? " del " + banco : "";
    }

    private String jornada(Empleado e) {
        return e.getHorasJornada() != null && e.getHorasJornada() < 8 ? " (jornada de " + e.getHorasJornada() + " h)" : "";
    }

    private LocalDate fechaConstancia(String periodo) {
        return planillaRepository.findByPeriodo(periodo)
                .map(p -> p.getFechaPago() != null ? p.getFechaPago() : p.getFechaCierre())
                .orElse(LocalDate.now()) != null
                ? planillaRepository.findByPeriodo(periodo).map(p -> p.getFechaPago() != null ? p.getFechaPago() : p.getFechaCierre()).orElse(LocalDate.now())
                : LocalDate.now();
    }

    private List<Integer> conGratificacion(String periodo) {
        List<Integer> ids = new ArrayList<>();
        for (BoletaGeneradaDTO b : planillaService.listarBoletas(periodo)) {
            if (b.getDetalle() != null && b.getDetalle().stream().anyMatch(l -> CodigosPlame.GRATIFICACION.equals(l.getCodigo()))) {
                ids.add(b.getIdEmpleado());
            }
        }
        if (ids.isEmpty()) throw new RuntimeException("No hay boletas con gratificación en " + periodo + ": procesa la planilla primero.");
        return ids;
    }

    private List<Integer> conCts(String periodo) {
        List<Integer> ids = new ArrayList<>();
        for (BoletaGeneradaDTO b : planillaService.listarBoletas(periodo)) {
            if (b.getDepositoCts() != null && b.getDepositoCts().signum() > 0) ids.add(b.getIdEmpleado());
        }
        if (ids.isEmpty()) throw new RuntimeException("No hay boletas con CTS en " + periodo + ": procesa la planilla primero.");
        return ids;
    }

    private interface Contenido {
        void escribir(Document doc) throws DocumentException;
    }

    private byte[] construir(Contenido contenido) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 45, 45);
            PdfWriter.getInstance(doc, out);
            doc.open();
            contenido.escribir(doc);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new RuntimeException("No se pudo generar la constancia: " + ex.getMessage());
        }
    }
}
