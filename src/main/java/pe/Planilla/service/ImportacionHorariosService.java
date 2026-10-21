package pe.Planilla.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.dto.ImportacionArchivoDTO;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO.ErrorFila;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.HorarioProgramado;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.HorarioProgramadoRepository;
import pe.Planilla.service.excel.ExcelLector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Horarios programados (el tareo): los asesores rotan de horario cada semana, así que cada día puede tener su propia
 * hora de entrada y salida, o ser descanso. Una fila por trabajador y día. Si un día no tiene fila, rige el horario
 * por defecto (lunes a viernes, 9:00 a 18:00).
 */
@Service
@RequiredArgsConstructor
public class ImportacionHorariosService {

    private static final int MAX_FILAS = 10000;
    private static final String HOJA = "Horarios";

    private final EmpleadoRepository empleadoRepository;
    private final HorarioProgramadoRepository horarioRepository;

    private static final List<String[]> COLUMNAS = List.of(
            new String[]{"numero_documento", "Sí", "Documento del trabajador (DNI o CE), el mismo que está registrado.", "45871236"},
            new String[]{"fecha", "Sí", "Día del horario, dd/mm/aaaa.", "27/10/2026"},
            new String[]{"entrada", "Sí*", "Hora de entrada, hh:mm. Obligatoria si no es descanso.", "09:00"},
            new String[]{"salida", "Sí*", "Hora de salida, hh:mm. Obligatoria si no es descanso.", "18:00"},
            new String[]{"descanso", "No", "SI si ese día descansa (no se marca falta). Vacío = NO.", "NO"},
            new String[]{"comentario", "No", "Libre: «Compensa», «Turno temprano»...", ""}
    );

    public byte[] generarPlantilla() {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(HOJA);
            Sheet instrucciones = libro.createSheet("Instrucciones");

            Font blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle azul = libro.createCellStyle();
            azul.setFont(blanca);
            azul.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            azul.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle gris = libro.createCellStyle();
            gris.setFont(negrita);
            gris.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            gris.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat formato = libro.createDataFormat();
            CellStyle texto = libro.createCellStyle();
            texto.setDataFormat(formato.getFormat("@"));
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));
            CellStyle horaEstilo = libro.createCellStyle();
            horaEstilo.setDataFormat(formato.getFormat("hh:mm"));

            Row cab = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                Cell c = cab.createCell(i);
                c.setCellValue(COLUMNAS.get(i)[0]);
                c.setCellStyle(i < 4 ? azul : gris);
                hoja.setColumnWidth(i, 20 * 256);
                switch (COLUMNAS.get(i)[0]) {
                    case "fecha" -> hoja.setDefaultColumnStyle(i, fecha);
                    case "entrada", "salida" -> hoja.setDefaultColumnStyle(i, horaEstilo);
                    default -> hoja.setDefaultColumnStyle(i, texto);
                }
            }
            hoja.createFreezePane(0, 1);
            DataValidationHelper dv = hoja.getDataValidationHelper();
            DataValidation v = dv.createValidation(dv.createExplicitListConstraint(new String[]{"SI", "NO"}),
                    new CellRangeAddressList(1, MAX_FILAS, 4, 4));
            v.setShowErrorBox(true);
            hoja.addValidationData(v);

            String[] notas = {
                    "HORARIOS PROGRAMADOS (TAREO)",
                    "1. Una fila por trabajador y día. Las columnas azules son obligatorias (entrada y salida, salvo que sea descanso).",
                    "2. Los días sin fila usan el horario por defecto: lunes a viernes de 9:00 a 18:00; sábado y domingo descanso.",
                    "3. Los feriados no necesitan fila: no se marca falta ni tardanza.",
                    "4. La tardanza se mide contra la entrada de ESTA hoja (9:01 con entrada 9:00 son 1 minuto de atraso).",
                    "5. Primero cargue los horarios y después las marcaciones del reloj, para que la tardanza y las faltas salgan bien.",
                    "6. Si una fila ya existía (mismo trabajador y día), se reemplaza. Si una fila tiene error no se guarda ninguna."
            };
            int f = 0;
            for (String nota : notas) {
                Cell c = instrucciones.createRow(f++).createCell(0);
                c.setCellValue(nota);
                if (f == 1) {
                    CellStyle titulo = libro.createCellStyle();
                    titulo.setFont(negrita);
                    c.setCellStyle(titulo);
                }
            }
            f++;
            Row th = instrucciones.createRow(f++);
            String[] titulos = {"Columna", "Obligatoria", "Qué poner", "Ejemplo"};
            for (int i = 0; i < titulos.length; i++) {
                Cell c = th.createCell(i);
                c.setCellValue(titulos[i]);
                c.setCellStyle(gris);
            }
            for (String[] col : COLUMNAS) {
                Row r = instrucciones.createRow(f++);
                for (int i = 0; i < 4; i++) r.createCell(i).setCellValue(col[i]);
            }
            instrucciones.setColumnWidth(0, 22 * 256);
            instrucciones.setColumnWidth(1, 12 * 256);
            instrucciones.setColumnWidth(2, 80 * 256);
            instrucciones.setColumnWidth(3, 20 * 256);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar la plantilla: " + e.getMessage());
        }
    }

    @Transactional
    public ImportacionArchivoDTO importar(MultipartFile archivo, boolean soloValidar) {
        if (archivo == null || archivo.isEmpty()) {
            throw new RuntimeException("Seleccione el Excel de horarios (.xlsx).");
        }
        String nombre = Optional.ofNullable(archivo.getOriginalFilename()).orElse("").toLowerCase();
        if (!nombre.endsWith(".xlsx") && !nombre.endsWith(".xls")) {
            throw new RuntimeException("El archivo debe ser un Excel (.xlsx). Descargue la plantilla y úsela.");
        }

        Map<String, Empleado> porDocumento = new HashMap<>();
        empleadoRepository.findAll().forEach(e -> porDocumento.putIfAbsent(e.getDni(), e));

        List<ErrorFila> errores = new ArrayList<>();
        Map<String, HorarioProgramado> aGuardar = new LinkedHashMap<>();
        int totalFilas = 0;
        LocalDate minFecha = null, maxFecha = null;
        Set<String> trabajadores = new HashSet<>();

        try (InputStream in = archivo.getInputStream(); Workbook libro = WorkbookFactory.create(in)) {
            Sheet hoja = Optional.ofNullable(libro.getSheet(HOJA)).orElse(libro.getSheetAt(0));
            DataFormatter fmt = new DataFormatter();

            Row cab = hoja.getRow(0);
            if (cab == null) throw new RuntimeException("El archivo está vacío.");
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : cab) {
                String k = ExcelLector.clave(fmt.formatCellValue(c));
                if (!k.isEmpty()) col.putIfAbsent(k, c.getColumnIndex());
            }
            for (String req : List.of("numerodocumento", "fecha", "entrada", "salida")) {
                if (!col.containsKey(req)) {
                    throw new RuntimeException("Falta la columna «" + req + "». Use la plantilla descargada desde el sistema.");
                }
            }

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (ExcelLector.filaVacia(fila, fmt)) continue;
                totalFilas++;
                int numeroFila = i + 1;
                if (totalFilas > MAX_FILAS) {
                    errores.add(new ErrorFila(numeroFila, "Se superó el máximo de " + MAX_FILAS + " filas por archivo."));
                    break;
                }
                try {
                    String doc = ExcelLector.textoNumerico(fila.getCell(col.get("numerodocumento")), fmt);
                    if (doc == null) throw new RuntimeException("El documento es obligatorio.");
                    Empleado emp = porDocumento.get(doc);
                    if (emp == null && doc.matches("\\d{1,7}")) emp = porDocumento.get(String.format("%08d", Long.parseLong(doc)));
                    if (emp == null) throw new RuntimeException("No hay un empleado con documento " + doc + ".");

                    LocalDate dia = ExcelLector.fecha(fila.getCell(col.get("fecha")), fmt, "fecha");
                    if (dia == null) throw new RuntimeException("La fecha es obligatoria.");

                    String textoDescanso = Optional.ofNullable(col.get("descanso")).map(ix -> ExcelLector.texto(fila.getCell(ix), fmt)).orElse(null);
                    boolean descanso = textoDescanso != null && List.of("si", "s", "x", "1", "true", "yes").contains(ExcelLector.clave(textoDescanso));

                    LocalTime entrada = ExcelLector.hora(fila.getCell(col.get("entrada")), fmt, "entrada");
                    LocalTime salida = ExcelLector.hora(fila.getCell(col.get("salida")), fmt, "salida");
                    if (!descanso) {
                        if (entrada == null || salida == null) throw new RuntimeException("Indique la entrada y la salida (o marque descanso = SI).");
                        if (!salida.isAfter(entrada)) throw new RuntimeException("La salida (" + salida + ") debe ser posterior a la entrada (" + entrada + ").");
                    }
                    String clave = emp.getIdEmpleado() + "|" + dia;
                    if (aGuardar.containsKey(clave)) throw new RuntimeException("Ese trabajador ya tiene otra fila para el " + dia + " en este archivo.");

                    HorarioProgramado h = horarioRepository
                            .findFirstByEmpleado_IdEmpleadoAndFecha(emp.getIdEmpleado(), dia).orElseGet(HorarioProgramado::new);
                    h.setEmpleado(emp);
                    h.setFecha(dia);
                    h.setDescanso(descanso);
                    h.setHoraEntrada(descanso ? null : entrada);
                    h.setHoraSalida(descanso ? null : salida);
                    Integer ixCom = col.get("comentario");
                    h.setComentario(ixCom == null ? null : ExcelLector.texto(fila.getCell(ixCom), fmt));
                    aGuardar.put(clave, h);
                    trabajadores.add(doc);
                    if (minFecha == null || dia.isBefore(minFecha)) minFecha = dia;
                    if (maxFecha == null || dia.isAfter(maxFecha)) maxFecha = dia;
                } catch (RuntimeException e) {
                    errores.add(new ErrorFila(numeroFila, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage());
        }
        if (totalFilas == 0 && errores.isEmpty()) {
            throw new RuntimeException("El archivo no tiene filas con datos debajo del encabezado.");
        }

        boolean importar = !soloValidar && errores.isEmpty();
        if (importar) {
            horarioRepository.saveAll(aGuardar.values());
        }
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("trabajadores", trabajadores.size());
        resumen.put("desde", minFecha == null ? null : minFecha.toString());
        resumen.put("hasta", maxFecha == null ? null : maxFecha.toString());
        resumen.put("diasDescanso", aGuardar.values().stream().filter(HorarioProgramado::isDescanso).count());
        return new ImportacionArchivoDTO(totalFilas, aGuardar.size(), importar, errores, List.of(), resumen);
    }
}
