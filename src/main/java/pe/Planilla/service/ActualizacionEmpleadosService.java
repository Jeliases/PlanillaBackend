package pe.Planilla.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.ImportacionArchivoDTO;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO.ErrorFila;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.repository.AfpRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.RegimenLaboralRepository;
import pe.Planilla.service.excel.ExcelLector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Actualización masiva de empleados existentes (lo que pidió la empresa: "solo los modificados que se escojan").
 * Se descarga un Excel con los empleados actuales, se cambian solo las celdas que corresponda y se sube.
 * Las celdas vacías NO borran datos: significan "sin cambio". Se busca por documento. Si una fila falla, no se guarda ninguna.
 */
@Service
@RequiredArgsConstructor
public class ActualizacionEmpleadosService {

    private static final String HOJA = "Empleados";
    private static final List<String> COLUMNAS = List.of("numero_documento", "nombres", "apellidos", "sueldo_base", "cargo", "area",
            "correo", "telefono", "regimen", "sistema_pensionario", "afp", "cuspp", "tipo_comision_afp", "asignacion_familiar",
            "banco", "cuenta_bancaria", "banco_cts", "cuenta_cts", "id_marcador");

    private final EmpleadoRepository empleadoRepository;
    private final RegimenLaboralRepository regimenRepository;
    private final AfpRepository afpRepository;
    private final EmpleadoService empleadoService;

    /** Excel con los empleados activos y sus datos actuales, listo para editar. */
    public byte[] exportarActuales() {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(HOJA);
            Sheet instrucciones = libro.createSheet("Instrucciones");
            CellStyle negrita = libro.createCellStyle();
            Font f = libro.createFont();
            f.setBold(true);
            negrita.setFont(f);
            CellStyle texto = libro.createCellStyle();
            texto.setDataFormat(libro.createDataFormat().getFormat("@"));

            Row cab = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                Cell c = cab.createCell(i);
                c.setCellValue(COLUMNAS.get(i));
                c.setCellStyle(negrita);
                hoja.setColumnWidth(i, 20 * 256);
                if (!COLUMNAS.get(i).equals("sueldo_base")) hoja.setDefaultColumnStyle(i, texto);
            }
            int fila = 1;
            for (Empleado e : empleadoRepository.findAll()) {
                if (!"ACTIVO".equals(e.getEstado())) continue;
                Row r = hoja.createRow(fila++);
                int c = 0;
                celdaTexto(r, c++, e.getDni(), texto);
                celdaTexto(r, c++, e.getNombres(), texto);
                celdaTexto(r, c++, e.getApellidos(), texto);
                r.createCell(c++).setCellValue(e.getSueldoBase().doubleValue());
                celdaTexto(r, c++, e.getCargo(), texto);
                celdaTexto(r, c++, e.getArea(), texto);
                celdaTexto(r, c++, e.getCorreo(), texto);
                celdaTexto(r, c++, e.getTelefono(), texto);
                celdaTexto(r, c++, e.getRegimenLaboral() != null ? e.getRegimenLaboral().getNombre() : null, texto);
                celdaTexto(r, c++, e.getSistemaPensionario(), texto);
                celdaTexto(r, c++, e.getAfp() != null ? e.getAfp().getNombre() : null, texto);
                celdaTexto(r, c++, e.getCuspp(), texto);
                celdaTexto(r, c++, e.getTipoComisionAfp(), texto);
                celdaTexto(r, c++, Boolean.TRUE.equals(e.getAsignacionFamiliar()) ? "SI" : "NO", texto);
                celdaTexto(r, c++, e.getBanco(), texto);
                celdaTexto(r, c++, e.getCuentaBancaria(), texto);
                celdaTexto(r, c++, e.getBancoCts(), texto);
                celdaTexto(r, c++, e.getCuentaCts(), texto);
                celdaTexto(r, c, e.getIdMarcador(), texto);
            }
            hoja.createFreezePane(1, 1);

            String[] notas = {
                    "ACTUALIZACIÓN MASIVA DE EMPLEADOS",
                    "1. Este archivo trae los empleados activos con sus datos actuales. Cambie solo las celdas que correspondan.",
                    "2. Una celda vacía significa «sin cambio»: no borra el dato que ya tiene el empleado.",
                    "3. No cambie el número de documento: es la llave para encontrar al empleado.",
                    "4. Puede borrar las filas de los empleados que no cambian, o dejarlas: solo se guardan las que tengan diferencias.",
                    "5. El sistema revisa todo el archivo antes de guardar. Si una fila tiene error, no se guarda ninguna.",
                    "6. Para cambiar de ONP a AFP indique también la AFP. Para asignación familiar escriba SI o NO."
            };
            int n = 0;
            for (String nota : notas) {
                Cell c = instrucciones.createRow(n++).createCell(0);
                c.setCellValue(nota);
                if (n == 1) c.setCellStyle(negrita);
            }
            instrucciones.setColumnWidth(0, 110 * 256);
            libro.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el archivo: " + e.getMessage());
        }
    }

    private void celdaTexto(Row r, int c, String v, CellStyle estilo) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v != null ? v : "");
        cell.setCellStyle(estilo);
    }

    @Transactional
    public ImportacionArchivoDTO actualizar(MultipartFile archivo, boolean soloValidar) {
        if (archivo == null || archivo.isEmpty()) throw new RuntimeException("Seleccione el Excel (.xlsx).");

        Map<String, RegimenLaboral> regimenes = new HashMap<>();
        regimenRepository.findAll().forEach(r -> regimenes.put(ExcelLector.clave(r.getNombre()), r));
        Map<String, Afp> afps = new HashMap<>();
        afpRepository.findAll().forEach(a -> afps.put(ExcelLector.clave(a.getNombre()), a));
        Map<String, Empleado> porDocumento = new HashMap<>();
        empleadoRepository.findAll().forEach(e -> porDocumento.putIfAbsent(e.getDni(), e));

        List<ErrorFila> errores = new ArrayList<>();
        List<Map<String, Object>> cambios = new ArrayList<>();
        Map<Integer, EmpleadoRequestDTO> aAplicar = new LinkedHashMap<>();
        int totalFilas = 0;

        try (Workbook libro = WorkbookFactory.create(archivo.getInputStream())) {
            Sheet hoja = Optional.ofNullable(libro.getSheet(HOJA)).orElse(libro.getSheetAt(0));
            DataFormatter fmt = new DataFormatter();
            Row cab = hoja.getRow(0);
            if (cab == null) throw new RuntimeException("El archivo está vacío.");
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : cab) {
                String k = ExcelLector.clave(fmt.formatCellValue(c));
                if (!k.isEmpty()) col.putIfAbsent(k, c.getColumnIndex());
            }
            if (!col.containsKey("numerodocumento")) {
                throw new RuntimeException("Falta la columna numero_documento. Descargue el archivo desde el sistema.");
            }

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (ExcelLector.filaVacia(fila, fmt)) continue;
                totalFilas++;
                int numeroFila = i + 1;
                try {
                    String doc = ExcelLector.textoNumerico(fila.getCell(col.get("numerodocumento")), fmt);
                    if (doc == null) throw new RuntimeException("Falta el número de documento.");
                    Empleado e = porDocumento.get(doc);
                    if (e == null && doc.matches("\\d{1,7}")) e = porDocumento.get(String.format("%08d", Long.parseLong(doc)));
                    if (e == null) throw new RuntimeException("No hay un empleado con documento " + doc + ". Esta pantalla solo actualiza; los nuevos van por Carga masiva.");
                    if (aAplicar.containsKey(e.getIdEmpleado())) throw new RuntimeException("El documento " + doc + " está repetido en el archivo.");

                    EmpleadoRequestDTO dto = new EmpleadoRequestDTO();
                    List<String> difs = new ArrayList<>();
                    String v;
                    if ((v = texto(fila, col, "nombres", fmt)) != null && !v.equals(e.getNombres())) { dto.setNombres(v); difs.add("nombres"); }
                    if ((v = texto(fila, col, "apellidos", fmt)) != null && !v.equals(e.getApellidos())) { dto.setApellidos(v); difs.add("apellidos"); }
                    BigDecimal sueldo = dinero(fila, col, fmt);
                    if (sueldo != null && sueldo.compareTo(e.getSueldoBase()) != 0) { dto.setSueldoBase(sueldo); difs.add("sueldo " + e.getSueldoBase() + " -> " + sueldo); }
                    if ((v = texto(fila, col, "cargo", fmt)) != null && !v.equals(e.getCargo())) { dto.setCargo(v); difs.add("cargo"); }
                    if ((v = texto(fila, col, "area", fmt)) != null && !v.equals(e.getArea())) { dto.setArea(v); difs.add("área"); }
                    if ((v = texto(fila, col, "correo", fmt)) != null && !v.equals(e.getCorreo())) { dto.setCorreo(v); difs.add("correo"); }
                    if ((v = textoNum(fila, col, "telefono", fmt)) != null && !v.equals(e.getTelefono())) { dto.setTelefono(v); difs.add("teléfono"); }
                    if ((v = texto(fila, col, "regimen", fmt)) != null) {
                        RegimenLaboral r = regimenes.get(ExcelLector.clave(v));
                        if (r == null) throw new RuntimeException("Régimen «" + v + "» no existe.");
                        if (e.getRegimenLaboral() == null || !r.getIdRegimen().equals(e.getRegimenLaboral().getIdRegimen())) { dto.setIdRegimen(r.getIdRegimen()); difs.add("régimen"); }
                    }
                    String sistema = texto(fila, col, "sistemapensionario", fmt);
                    String nombreAfp = texto(fila, col, "afp", fmt);
                    if (sistema != null && !sistema.equalsIgnoreCase(e.getSistemaPensionario())) { dto.setSistemaPensionario(sistema); difs.add("sistema pensionario"); }
                    if (nombreAfp != null) {
                        Afp a = afps.get(ExcelLector.clave(nombreAfp));
                        if (a == null) throw new RuntimeException("AFP «" + nombreAfp + "» no existe.");
                        if (e.getAfp() == null || !a.getIdAfp().equals(e.getAfp().getIdAfp())) { dto.setIdAfp(a.getIdAfp()); difs.add("AFP"); }
                        if (dto.getSistemaPensionario() == null && !"AFP".equals(e.getSistemaPensionario())) { dto.setSistemaPensionario("AFP"); difs.add("sistema pensionario"); }
                    }
                    if ((v = texto(fila, col, "cuspp", fmt)) != null && !v.equals(e.getCuspp())) { dto.setCuspp(v); difs.add("CUSPP"); }
                    if ((v = texto(fila, col, "tipocomisionafp", fmt)) != null && !v.equalsIgnoreCase(e.getTipoComisionAfp())) { dto.setTipoComisionAfp(v); difs.add("comisión AFP"); }
                    if ((v = texto(fila, col, "asignacionfamiliar", fmt)) != null) {
                        boolean si = List.of("si", "s", "x", "1", "true", "yes").contains(ExcelLector.clave(v));
                        if (si != Boolean.TRUE.equals(e.getAsignacionFamiliar())) { dto.setAsignacionFamiliar(si); difs.add("asignación familiar"); }
                    }
                    if ((v = texto(fila, col, "banco", fmt)) != null && !v.equals(e.getBanco())) { dto.setBanco(v); difs.add("banco"); }
                    if ((v = textoNum(fila, col, "cuentabancaria", fmt)) != null && !v.equals(e.getCuentaBancaria())) { dto.setCuentaBancaria(v); difs.add("cuenta"); }
                    if ((v = texto(fila, col, "bancocts", fmt)) != null && !v.equals(e.getBancoCts())) { dto.setBancoCts(v); difs.add("banco CTS"); }
                    if ((v = textoNum(fila, col, "cuentacts", fmt)) != null && !v.equals(e.getCuentaCts())) { dto.setCuentaCts(v); difs.add("cuenta CTS"); }
                    if ((v = textoNum(fila, col, "idmarcador", fmt)) != null && !v.equals(e.getIdMarcador())) { dto.setIdMarcador(v); difs.add("ID marcador"); }

                    if (difs.isEmpty()) continue; // sin cambios: no se toca
                    aAplicar.put(e.getIdEmpleado(), dto);
                    Map<String, Object> cambio = new LinkedHashMap<>();
                    cambio.put("empleado", e.getNombres() + " " + e.getApellidos());
                    cambio.put("cambios", String.join(", ", difs));
                    cambios.add(cambio);
                } catch (RuntimeException ex) {
                    errores.add(new ErrorFila(numeroFila, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo leer el archivo: " + ex.getMessage());
        }

        boolean aplicar = !soloValidar && errores.isEmpty();
        if (aplicar) {
            // actualizar() valida cada empleado completo; cualquier error revierte toda la transacción
            for (Map.Entry<Integer, EmpleadoRequestDTO> en : aAplicar.entrySet()) {
                empleadoService.actualizar(en.getKey(), en.getValue());
            }
        }
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("empleadosConCambios", aAplicar.size());
        resumen.put("sinCambios", totalFilas - aAplicar.size() - errores.size());
        resumen.put("detalleCambios", cambios);
        return new ImportacionArchivoDTO(totalFilas, aAplicar.size(), aplicar, errores, List.of(), resumen);
    }

    private String texto(Row fila, Map<String, Integer> col, String clave, DataFormatter fmt) {
        Integer ix = col.get(clave);
        return ix == null ? null : ExcelLector.texto(fila.getCell(ix), fmt);
    }

    private String textoNum(Row fila, Map<String, Integer> col, String clave, DataFormatter fmt) {
        Integer ix = col.get(clave);
        return ix == null ? null : ExcelLector.textoNumerico(fila.getCell(ix), fmt);
    }

    private BigDecimal dinero(Row fila, Map<String, Integer> col, DataFormatter fmt) {
        Integer ix = col.get("sueldobase");
        if (ix == null) return null;
        Cell c = fila.getCell(ix);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(c.getNumericCellValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        String s = fmt.formatCellValue(c).trim().replace("S/", "").replace(",", "").trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Sueldo inválido «" + s + "».");
        }
    }
}
