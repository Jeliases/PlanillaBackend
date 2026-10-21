package pe.Planilla.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.TipoDocumento;
import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO.ErrorFila;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.repository.AfpRepository;
import pe.Planilla.repository.RegimenLaboralRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Carga masiva de empleados desde Excel.
 * Reglas de seguridad para el dinero: se revisa TODO el archivo primero; si una sola fila tiene error no se guarda
 * ninguna (así nunca queda una carga a medias). Solo crea empleados nuevos: no modifica a los existentes.
 */
@Service
@RequiredArgsConstructor
public class ImportacionEmpleadosService {

    private static final int MAX_FILAS = 2000;
    private static final String HOJA = "Empleados";

    private final EmpleadoService empleadoService;
    private final RegimenLaboralRepository regimenRepository;
    private final AfpRepository afpRepository;

    private record Columna(String clave, boolean obligatoria, String descripcion, String ejemplo) {}

    // Orden = orden de las columnas en la plantilla. El encabezado de la plantilla es la clave.
    private static final List<Columna> COLUMNAS = List.of(
            new Columna("tipo_documento", false, "DNI, CE, PASAPORTE, PTP o CSR. Vacío = DNI.", "DNI"),
            new Columna("numero_documento", true, "DNI: 8 dígitos. Otros: 6 a 15 letras o números, sin espacios.", "45871236"),
            new Columna("nombres", true, "Nombres del trabajador.", "María Elena"),
            new Columna("apellidos", true, "Apellido paterno y materno.", "Quispe Rojas"),
            new Columna("fecha_nacimiento", false, "dd/mm/aaaa.", "14/03/1992"),
            new Columna("fecha_ingreso", true, "dd/mm/aaaa. Define los días del primer mes y los meses de gratificación y CTS.", "01/02/2025"),
            new Columna("cargo", false, "Puesto de trabajo.", "Asesor comercial"),
            new Columna("area", false, "Área, sede o campaña.", "Ventas"),
            new Columna("correo", false, "Correo electrónico (para enviar la boleta).", "maria.quispe@empresa.com"),
            new Columna("telefono", false, "Celular o teléfono.", "987654321"),
            new Columna("sueldo_base", true, "Sueldo mensual en soles, mayor a 0. Sin el signo S/.", "1500.00"),
            new Columna("regimen", false, "Régimen laboral, de la lista (hoja Listas). Vacío = Régimen General.", "Régimen General"),
            new Columna("sistema_pensionario", false, "ONP, AFP o NINGUNO (jubilado / no aportante). Vacío = ONP (o AFP si indica una AFP).", "AFP"),
            new Columna("afp", false, "Obligatoria si es AFP. Elija de la lista.", "Habitat"),
            new Columna("cuspp", false, "Código CUSPP de la AFP (opcional).", "649871AFMRQ2"),
            new Columna("tipo_comision_afp", false, "FLUJO (por defecto) o MIXTA. La mixta no paga comisión por flujo: solo 10% + prima.", "FLUJO"),
            new Columna("asignacion_familiar", false, "SI o NO. Vacío = NO.", "NO"),
            new Columna("banco", false, "Banco donde se deposita el sueldo.", "BCP"),
            new Columna("cuenta_bancaria", false, "Número de cuenta o CCI del sueldo.", "19412345678901"),
            new Columna("banco_cts", false, "Banco donde se deposita la CTS.", "Interbank"),
            new Columna("cuenta_cts", false, "Número de cuenta de la CTS.", "8983001234567"),
            new Columna("id_marcador", false, "ID con el que marca en el reloj biométrico, si es distinto al documento (ej. 10).", "10"),
            new Columna("horas_jornada", false, "Horas de trabajo al día: 8 (jornada completa) o 4 (media jornada: el básico se paga proporcional). Vacío = 8.", "8"),
            new Columna("fecha_fin_contrato", false, "dd/mm/aaaa. Fin del contrato a plazo fijo; vacío o «Indefinido» = sin fecha.", "31/12/2026"),
            new Columna("supervisor", false, "Supervisor o jefe directo.", "Abel"),
            new Columna("sede", false, "Sede o lugar de trabajo.", "LIMA"),
            new Columna("cartera", false, "Cartera o campaña.", "ADM")
    );

    /** Valores de la columna «REGIMEN» de la planilla de la contadora que en realidad son el sistema de pensiones. */
    private static final Set<String> VALORES_PENSION = Set.of("onp", "afp", "noaportante", "ninguno", "sinaporte", "sinpension", "jubilado");

    // Otros nombres que se aceptan para las columnas (el usuario puede escribir "DNI" en vez de numero_documento)
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("dni", "numerodocumento"), Map.entry("documento", "numerodocumento"),
            Map.entry("nrodocumento", "numerodocumento"), Map.entry("nrodoc", "numerodocumento"),
            Map.entry("tipodoc", "tipodocumento"), Map.entry("nombre", "nombres"), Map.entry("apellido", "apellidos"),
            Map.entry("sueldo", "sueldobase"), Map.entry("remuneracion", "sueldobase"),
            Map.entry("sistemapension", "sistemapensionario"), Map.entry("sistema", "sistemapensionario"),
            Map.entry("asignacion", "asignacionfamiliar"), Map.entry("email", "correo"),
            Map.entry("fechaingresoempresa", "fechaingreso"), Map.entry("fechadenacimiento", "fechanacimiento"),
            // Encabezados de la hoja PLANILLA mensual de la contadora (se acepta tal cual, cabecera en la fila 5)
            Map.entry("nombredeltrabajador", "nombrecompleto"), Map.entry("nombretrabajador", "nombrecompleto"),
            Map.entry("trabajador", "nombrecompleto"), Map.entry("apellidosynombres", "nombrecompleto"),
            Map.entry("nombresyapellidos", "nombrecompleto"), Map.entry("nombrecompleto", "nombrecompleto"),
            Map.entry("fechadeingreso", "fechaingreso"), Map.entry("ingreso", "fechaingreso"),
            Map.entry("terminodecontrato", "fechafincontrato"), Map.entry("terminocontrato", "fechafincontrato"),
            Map.entry("fincontrato", "fechafincontrato"), Map.entry("fechafindecontrato", "fechafincontrato"),
            Map.entry("fechadecese", "fechacese"), Map.entry("cese", "fechacese"),
            Map.entry("centrodecostos", "area"), Map.entry("centrodecosto", "area"), Map.entry("centrocostos", "area"),
            Map.entry("tipodecomision", "tipocomisionafp"), Map.entry("tipocomision", "tipocomisionafp"),
            Map.entry("basico", "sueldobase"), Map.entry("sueldobasico", "sueldobase"),
            Map.entry("hras", "horasjornada"), Map.entry("horas", "horasjornada"), Map.entry("jornada", "horasjornada"),
            Map.entry("asigfam", "asignacionfamiliar"), Map.entry("asignacionfam", "asignacionfamiliar"),
            Map.entry("cuenta", "cuentabancaria"), Map.entry("nrocuenta", "cuentabancaria"), Map.entry("cci", "cuentabancaria"),
            Map.entry("cod", "idmarcador"), Map.entry("codigo", "idmarcador"),
            Map.entry("regimenpensionario", "sistemapensionario"), Map.entry("pension", "sistemapensionario"));

    // ================================================================= Plantilla

    public byte[] generarPlantilla() {
        List<RegimenLaboral> regimenes = regimenRepository.findAll();
        List<Afp> afps = afpRepository.findAll();

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(HOJA);
            Sheet instrucciones = libro.createSheet("Instrucciones");
            Sheet listas = libro.createSheet("Listas");

            // --- estilos
            Font negrita = libro.createFont();
            negrita.setBold(true);
            Font blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());

            CellStyle encObligatorio = libro.createCellStyle();
            encObligatorio.setFont(blanca);
            encObligatorio.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            encObligatorio.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle encOpcional = libro.createCellStyle();
            encOpcional.setFont(negrita);
            encOpcional.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            encOpcional.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat formato = libro.createDataFormat();
            CellStyle texto = libro.createCellStyle();
            texto.setDataFormat(formato.getFormat("@")); // conserva ceros a la izquierda (DNI, cuentas, teléfonos)
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));
            CellStyle dinero = libro.createCellStyle();
            dinero.setDataFormat(formato.getFormat("#,##0.00"));

            // --- hoja de datos: solo encabezados (sin fila de ejemplo, para que nadie importe datos falsos)
            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                Columna col = COLUMNAS.get(i);
                Cell celda = cabecera.createCell(i);
                celda.setCellValue(col.clave());
                celda.setCellStyle(col.obligatoria() ? encObligatorio : encOpcional);
                hoja.setColumnWidth(i, Math.max(16, col.clave().length() + 4) * 256);
                switch (col.clave()) {
                    case "fecha_nacimiento", "fecha_ingreso" -> hoja.setDefaultColumnStyle(i, fecha);
                    case "sueldo_base" -> hoja.setDefaultColumnStyle(i, dinero);
                    default -> hoja.setDefaultColumnStyle(i, texto);
                }
            }
            hoja.createFreezePane(0, 1);

            // --- hoja de listas (alimenta los desplegables)
            Row lc = listas.createRow(0);
            lc.createCell(0).setCellValue("tipo_documento");
            lc.createCell(1).setCellValue("regimen");
            lc.createCell(2).setCellValue("afp");
            TipoDocumento[] tipos = TipoDocumento.values();
            for (int i = 0; i < tipos.length; i++) {
                listas.createRow(i + 1).createCell(0).setCellValue(tipos[i].name());
            }
            for (int i = 0; i < regimenes.size(); i++) {
                filaDe(listas, i + 1).createCell(1).setCellValue(regimenes.get(i).getNombre());
            }
            for (int i = 0; i < afps.size(); i++) {
                filaDe(listas, i + 1).createCell(2).setCellValue(afps.get(i).getNombre());
            }
            for (int i = 0; i < 3; i++) listas.setColumnWidth(i, 30 * 256);

            DataValidationHelper dv = hoja.getDataValidationHelper();
            agregarLista(hoja, dv, indice("tipo_documento"), dv.createFormulaListConstraint("Listas!$A$2:$A$" + (tipos.length + 1)));
            if (!regimenes.isEmpty()) {
                agregarLista(hoja, dv, indice("regimen"), dv.createFormulaListConstraint("Listas!$B$2:$B$" + (regimenes.size() + 1)));
            }
            if (!afps.isEmpty()) {
                agregarLista(hoja, dv, indice("afp"), dv.createFormulaListConstraint("Listas!$C$2:$C$" + (afps.size() + 1)));
            }
            agregarLista(hoja, dv, indice("sistema_pensionario"), dv.createExplicitListConstraint(new String[]{"ONP", "AFP", "NINGUNO"}));
            agregarLista(hoja, dv, indice("horas_jornada"), dv.createExplicitListConstraint(new String[]{"8", "6", "4"}));
            agregarLista(hoja, dv, indice("asignacion_familiar"), dv.createExplicitListConstraint(new String[]{"SI", "NO"}));
            agregarLista(hoja, dv, indice("tipo_comision_afp"), dv.createExplicitListConstraint(new String[]{"FLUJO", "MIXTA"}));

            // --- instrucciones
            int f = 0;
            String[] notas = {
                    "CARGA MASIVA DE EMPLEADOS",
                    "1. Llene la hoja «Empleados»: una fila por trabajador. Las columnas con encabezado azul son obligatorias.",
                    "2. No cambie los nombres de los encabezados (el orden de las columnas sí puede variar).",
                    "   También puede subir la hoja «PLANILLA» del Excel mensual de la contadora tal cual (cabecera en la fila 5):",
                    "   se leen NOMBRE DEL TRABAJADOR, DNI, fechas, CARGO, SUPERVISOR, SEDE, CENTRO DE COSTOS, CARTERA, REGIMEN (pensión),",
                    "   CUSPP, TIPO DE COMISIÓN, BASICO, hras, ASIG. FAM., Cuenta y Banco; las columnas del mes se ignoran.",
                    "3. Fechas como dd/mm/aaaa. Montos sin «S/» ni separador de miles.",
                    "4. Régimen, AFP, tipo de documento, sistema pensionario y asignación familiar se eligen de la lista desplegable.",
                    "5. El sistema revisa todo el archivo antes de guardar. Si una fila tiene error, no se guarda ninguna: corrija y vuelva a subirlo.",
                    "6. Solo crea trabajadores nuevos. Un documento que ya existe se reporta como error (no se modifica).",
                    "7. Máximo 2,000 filas por archivo."
            };
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
                c.setCellStyle(encOpcional);
            }
            for (Columna col : COLUMNAS) {
                Row r = instrucciones.createRow(f++);
                r.createCell(0).setCellValue(col.clave());
                r.createCell(1).setCellValue(col.obligatoria() ? "Sí" : "No");
                r.createCell(2).setCellValue(col.descripcion());
                r.createCell(3).setCellValue(col.ejemplo());
            }
            instrucciones.setColumnWidth(0, 24 * 256);
            instrucciones.setColumnWidth(1, 12 * 256);
            instrucciones.setColumnWidth(2, 80 * 256);
            instrucciones.setColumnWidth(3, 30 * 256);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar la plantilla: " + e.getMessage());
        }
    }

    private Row filaDe(Sheet hoja, int indice) {
        Row fila = hoja.getRow(indice);
        return fila != null ? fila : hoja.createRow(indice);
    }

    private int indice(String clave) {
        for (int i = 0; i < COLUMNAS.size(); i++) {
            if (COLUMNAS.get(i).clave().equals(clave)) return i;
        }
        throw new IllegalStateException("Columna desconocida: " + clave);
    }

    private void agregarLista(Sheet hoja, DataValidationHelper dv, int columna, DataValidationConstraint restriccion) {
        DataValidation v = dv.createValidation(restriccion, new CellRangeAddressList(1, MAX_FILAS, columna, columna));
        v.setShowErrorBox(true);
        v.setErrorStyle(DataValidation.ErrorStyle.WARNING);
        v.createErrorBox("Valor no válido", "Elija un valor de la lista.");
        hoja.addValidationData(v);
    }

    // ================================================================ Importación

    @Transactional
    public ImportacionEmpleadosDTO importar(MultipartFile archivo, boolean soloValidar) {
        if (archivo == null || archivo.isEmpty()) {
            throw new RuntimeException("Seleccione un archivo Excel (.xlsx).");
        }
        String nombre = Optional.ofNullable(archivo.getOriginalFilename()).orElse("").toLowerCase();
        if (!nombre.endsWith(".xlsx") && !nombre.endsWith(".xls")) {
            throw new RuntimeException("El archivo debe ser un Excel (.xlsx). Descargue la plantilla y úsela.");
        }

        Map<String, RegimenLaboral> regimenes = new HashMap<>();
        regimenRepository.findAll().forEach(r -> regimenes.put(clave(r.getNombre()), r));
        Map<String, Afp> afps = new HashMap<>();
        afpRepository.findAll().forEach(a -> afps.put(clave(a.getNombre()), a));

        List<ErrorFila> errores = new ArrayList<>();
        List<Empleado> validos = new ArrayList<>();
        int totalFilas = 0;

        try (InputStream in = archivo.getInputStream(); Workbook libro = WorkbookFactory.create(in)) {
            Sheet hoja = Optional.ofNullable(libro.getSheet(HOJA)).orElse(libro.getSheetAt(0));
            DataFormatter fmt = new DataFormatter();

            // Encabezados: la fila que tiene «nombre» y «dni/documento» (la hoja de la contadora los tiene en la fila 5,
            // con filas de fórmulas arriba); si no se encuentra, la primera fila con contenido
            int filaCab = -1;
            int primeraConContenido = -1;
            for (int i = 0; i <= Math.min(hoja.getLastRowNum(), 15); i++) {
                Row r = hoja.getRow(i);
                if (filaVacia(r, fmt)) continue;
                if (primeraConContenido < 0) primeraConContenido = i;
                boolean tieneNombre = false, tieneDocumento = false;
                for (Cell c : r) {
                    String k = ALIAS.getOrDefault(clave(fmt.formatCellValue(c)), clave(fmt.formatCellValue(c)));
                    if (k.startsWith("nombre")) tieneNombre = true;
                    if (k.equals("numerodocumento")) tieneDocumento = true;
                }
                if (tieneNombre && tieneDocumento) {
                    filaCab = i;
                    break;
                }
            }
            if (filaCab < 0) filaCab = primeraConContenido;
            if (filaCab < 0) throw new RuntimeException("El archivo está vacío.");

            Map<String, Integer> col = new HashMap<>();
            for (Cell c : hoja.getRow(filaCab)) {
                String k = clave(fmt.formatCellValue(c));
                if (!k.isEmpty()) col.putIfAbsent(ALIAS.getOrDefault(k, k), c.getColumnIndex());
            }
            Set<String> presentes = new HashSet<>(col.keySet());
            if (presentes.contains("nombrecompleto")) { // «NOMBRE DEL TRABAJADOR» trae apellidos y nombres juntos
                presentes.add("nombres");
                presentes.add("apellidos");
            }
            List<String> faltantes = COLUMNAS.stream()
                    .filter(Columna::obligatoria)
                    .map(Columna::clave)
                    .filter(c -> !presentes.contains(clave(c)))
                    .toList();
            if (!faltantes.isEmpty()) {
                throw new RuntimeException("Faltan columnas obligatorias: " + String.join(", ", faltantes)
                        + ". Use la plantilla descargada desde el sistema.");
            }

            Map<String, Integer> vistos = new HashMap<>(); // documento -> primera fila donde aparece

            for (int i = filaCab + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (filaVacia(fila, fmt)) continue;
                // Filas de totales o de notas (sin documento ni nombre) no son trabajadores
                if (texto(fila, col, "numerodocumento", fmt) == null && texto(fila, col, "nombres", fmt) == null
                        && texto(fila, col, "nombrecompleto", fmt) == null) continue;
                totalFilas++;
                int numeroFila = i + 1;
                if (totalFilas > MAX_FILAS) {
                    errores.add(new ErrorFila(numeroFila, "Se superó el máximo de " + MAX_FILAS + " filas por archivo."));
                    break;
                }

                try {
                    EmpleadoRequestDTO dto = leerFila(fila, col, fmt, regimenes, afps);
                    String claveDoc = TipoDocumento.desdeTexto(dto.getTipoDocumento()).name() + "|" + (dto.getDni() == null ? "" : dto.getDni());
                    Integer primera = vistos.putIfAbsent(claveDoc, numeroFila);
                    if (primera != null) {
                        throw new RuntimeException("El documento " + dto.getDni() + " está repetido en el archivo (ya aparece en la fila " + primera + ").");
                    }
                    Empleado empleado = empleadoService.construirEmpleado(dto);
                    if (dto.getFechaCese() != null) { // la hoja de la contadora trae también a los que ya cesaron
                        if (dto.getFechaIngreso() != null && dto.getFechaCese().isBefore(dto.getFechaIngreso())) {
                            throw new RuntimeException("La fecha de cese es anterior a la de ingreso.");
                        }
                        empleado.setFechaCese(dto.getFechaCese());
                        empleado.setEstado("CESADO");
                        empleado.setMotivoCese("Importado con fecha de cese");
                    }
                    validos.add(empleado);
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
        List<EmpleadoResponseDTO> resultado = importar
                ? empleadoService.guardarLote(validos)
                : validos.stream().map(empleadoService::aDTO).toList();

        return new ImportacionEmpleadosDTO(totalFilas, validos.size(), importar, errores, resultado);
    }

    // ----------------------------------------------------------------- lectura

    private EmpleadoRequestDTO leerFila(Row fila, Map<String, Integer> col, DataFormatter fmt,
                                        Map<String, RegimenLaboral> regimenes, Map<String, Afp> afps) {
        EmpleadoRequestDTO dto = new EmpleadoRequestDTO();

        TipoDocumento tipo = TipoDocumento.desdeTexto(texto(fila, col, "tipodocumento", fmt));
        dto.setTipoDocumento(tipo.name());
        String numero = textoNumerico(fila, col, "numerodocumento", fmt);
        // Excel quita los ceros a la izquierda de un DNI escrito como número: se recuperan
        if (tipo == TipoDocumento.DNI && numero != null && numero.matches("\\d{1,7}") && esNumerica(fila, col, "numerodocumento")) {
            numero = String.format("%08d", Long.parseLong(numero));
        }
        dto.setDni(numero);

        dto.setNombres(texto(fila, col, "nombres", fmt));
        dto.setApellidos(texto(fila, col, "apellidos", fmt));
        String completo = texto(fila, col, "nombrecompleto", fmt);
        if (completo != null && dto.getNombres() == null && dto.getApellidos() == null) {
            // «APELLIDO PATERNO APELLIDO MATERNO NOMBRES» (como la planilla de la contadora): dos apellidos y el resto nombres
            String[] partes = completo.trim().split("\\s+");
            if (partes.length < 2) {
                throw new RuntimeException("Escriba apellidos y nombres en NOMBRE DEL TRABAJADOR (vino «" + completo + "»).");
            }
            int nApellidos = partes.length >= 3 ? 2 : 1;
            dto.setApellidos(String.join(" ", Arrays.copyOfRange(partes, 0, nApellidos)));
            dto.setNombres(String.join(" ", Arrays.copyOfRange(partes, nApellidos, partes.length)));
        }
        dto.setFechaNacimiento(fecha(fila, col, "fechanacimiento", "fecha_nacimiento", fmt));
        dto.setFechaIngreso(fecha(fila, col, "fechaingreso", "fecha_ingreso", fmt));
        dto.setFechaFinContrato(fechaOpcional(fila, col, "fechafincontrato", "fecha_fin_contrato", fmt));
        dto.setFechaCese(fechaOpcional(fila, col, "fechacese", "fecha_cese", fmt));
        dto.setCargo(texto(fila, col, "cargo", fmt));
        dto.setArea(texto(fila, col, "area", fmt));
        dto.setSupervisor(texto(fila, col, "supervisor", fmt));
        dto.setSede(texto(fila, col, "sede", fmt));
        dto.setCartera(texto(fila, col, "cartera", fmt));
        dto.setCorreo(texto(fila, col, "correo", fmt));
        dto.setTelefono(textoNumerico(fila, col, "telefono", fmt));
        dto.setSueldoBase(dinero(fila, col, "sueldobase", "sueldo_base", fmt));
        dto.setBanco(texto(fila, col, "banco", fmt));
        dto.setCuentaBancaria(textoNumerico(fila, col, "cuentabancaria", fmt));
        dto.setBancoCts(texto(fila, col, "bancocts", fmt));
        dto.setCuentaCts(textoNumerico(fila, col, "cuentacts", fmt));
        dto.setIdMarcador(textoNumerico(fila, col, "idmarcador", fmt));
        String horas = texto(fila, col, "horasjornada", fmt);
        if (horas != null) {
            try {
                dto.setHorasJornada(new java.math.BigDecimal(horas.replace(",", ".")).intValueExact());
            } catch (ArithmeticException | NumberFormatException ex) {
                throw new RuntimeException("horas_jornada debe ser un número entero entre 1 y 8 (vacío = 8).");
            }
        }
        String cuspp = texto(fila, col, "cuspp", fmt);
        dto.setCuspp(cuspp != null && cuspp.matches("(?i)x+") ? null : cuspp); // «xxxxxxxx» es un relleno, no un CUSPP
        String tipoComision = texto(fila, col, "tipocomisionafp", fmt);
        if (tipoComision != null) { // «Com.Flujo» / «Com.Mixta» de la planilla de la contadora
            String k = clave(tipoComision);
            tipoComision = k.contains("mixt") ? "MIXTA" : (k.contains("flujo") ? "FLUJO" : tipoComision);
        }
        dto.setTipoComisionAfp(tipoComision);

        // Régimen laboral: por nombre; vacío = Régimen General. En la planilla de la contadora la columna «REGIMEN»
        // es en realidad el sistema de pensiones (ONP, Integra, NO APORTANTE...): se detecta por el valor.
        String nombreRegimen = texto(fila, col, "regimen", fmt);
        String pensionDesdeRegimen = null;
        if (nombreRegimen != null && (VALORES_PENSION.contains(clave(nombreRegimen)) || afps.containsKey(clave(nombreRegimen)))) {
            pensionDesdeRegimen = nombreRegimen;
            nombreRegimen = null;
        }
        RegimenLaboral regimen = nombreRegimen == null ? regimenes.get(clave("Régimen General")) : regimenes.get(clave(nombreRegimen));
        if (regimen == null) {
            throw new RuntimeException("Régimen «" + (nombreRegimen == null ? "Régimen General" : nombreRegimen) + "» no existe. Válidos: "
                    + regimenes.values().stream().map(RegimenLaboral::getNombre).sorted().toList());
        }
        dto.setIdRegimen(regimen.getIdRegimen());

        // Pensión
        String sistema = texto(fila, col, "sistemapensionario", fmt);
        if (sistema == null) sistema = pensionDesdeRegimen;
        String nombreAfp = texto(fila, col, "afp", fmt);
        if (sistema != null && afps.containsKey(clave(sistema))) { // vino el nombre de la AFP en vez de «AFP»
            nombreAfp = sistema;
            sistema = "AFP";
        }
        if (sistema == null) sistema = nombreAfp != null ? "AFP" : "ONP";
        sistema = sistema.trim().toUpperCase();
        if (sistema.startsWith("SIN") || sistema.startsWith("NO ") || sistema.startsWith("NING")) sistema = "NINGUNO";
        dto.setSistemaPensionario(sistema);
        if ("AFP".equals(dto.getSistemaPensionario())) {
            if (nombreAfp == null) {
                throw new RuntimeException("Indique la AFP (el trabajador está en AFP).");
            }
            Afp afp = afps.get(clave(nombreAfp));
            if (afp == null) {
                throw new RuntimeException("AFP «" + nombreAfp + "» no existe. Válidas: "
                        + afps.values().stream().map(Afp::getNombre).sorted().toList());
            }
            dto.setIdAfp(afp.getIdAfp());
        }

        dto.setAsignacionFamiliar(siNo(texto(fila, col, "asignacionfamiliar", fmt)));
        return dto;
    }

    private Cell celda(Row fila, Map<String, Integer> col, String clave) {
        Integer idx = col.get(clave);
        return idx == null ? null : fila.getCell(idx);
    }

    /** Una celda con fórmula se lee por su resultado guardado (la hoja de la contadora está llena de fórmulas). */
    private boolean esNumerica(Cell c) {
        return c != null && (c.getCellType() == CellType.NUMERIC
                || (c.getCellType() == CellType.FORMULA && c.getCachedFormulaResultType() == CellType.NUMERIC));
    }

    private String textoCelda(Cell c, DataFormatter fmt) {
        if (c == null) return "";
        if (c.getCellType() == CellType.FORMULA) {
            return switch (c.getCachedFormulaResultType()) {
                case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
                case STRING -> c.getStringCellValue().trim();
                case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
                default -> "";
            };
        }
        return fmt.formatCellValue(c).trim();
    }

    private String texto(Row fila, Map<String, Integer> col, String clave, DataFormatter fmt) {
        String s = textoCelda(celda(fila, col, clave), fmt);
        return s.isEmpty() || s.equals("-") ? null : s; // «-» es el vacío de la hoja de la contadora
    }

    private boolean esNumerica(Row fila, Map<String, Integer> col, String clave) {
        return esNumerica(celda(fila, col, clave));
    }

    /** Para documentos, teléfonos y cuentas: si Excel lo guardó como número, se lee sin decimales ni notación científica. */
    private String textoNumerico(Row fila, Map<String, Integer> col, String clave, DataFormatter fmt) {
        Cell c = celda(fila, col, clave);
        if (c == null) return null;
        if (esNumerica(c)) {
            return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        String s = textoCelda(c, fmt);
        return s.isEmpty() || s.equals("-") ? null : s.replace(" ", "");
    }

    // STRICT: una fecha imposible (31/02/2025) se rechaza en vez de "corregirse" en silencio al 28/02
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(ResolverStyle.STRICT),   // 10/09/26
            DateTimeFormatter.ISO_LOCAL_DATE);

    /** Fecha que puede venir como texto no fecha («Indefinido», «-»): en ese caso es null. */
    private LocalDate fechaOpcional(Row fila, Map<String, Integer> col, String clave, String nombreColumna, DataFormatter fmt) {
        Cell c = celda(fila, col, clave);
        if (c == null) return null;
        String s = textoCelda(c, fmt);
        if (!esNumerica(c) && (s.isEmpty() || s.equals("-") || clave(s).startsWith("indefinid") || clave(s).startsWith("indeterminad"))) {
            return null;
        }
        return fecha(fila, col, clave, nombreColumna, fmt);
    }

    private LocalDate fecha(Row fila, Map<String, Integer> col, String clave, String nombreColumna, DataFormatter fmt) {
        Cell c = celda(fila, col, clave);
        if (c == null) return null;
        if (esNumerica(c)) {
            return DateUtil.getJavaDate(c.getNumericCellValue()).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        String s = textoCelda(c, fmt);
        if (s.isEmpty() || s.equals("-")) return null;
        for (DateTimeFormatter f : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignorada) {
                // se prueba el siguiente formato
            }
        }
        throw new RuntimeException("Fecha inválida «" + s + "» en " + nombreColumna + ". Use dd/mm/aaaa.");
    }

    private BigDecimal dinero(Row fila, Map<String, Integer> col, String clave, String nombreColumna, DataFormatter fmt) {
        Cell c = celda(fila, col, clave);
        if (c == null) return null;
        if (esNumerica(c)) return BigDecimal.valueOf(c.getNumericCellValue());
        String s = textoCelda(c, fmt).replace("S/", "").replace("s/", "").replace(" ", "");
        if (s.isEmpty() || s.equals("-")) return null;
        if (s.matches("\\d{1,3}(,\\d{3})+(\\.\\d+)?")) s = s.replace(",", "");  // 1,500.00
        else if (s.matches("\\d+,\\d{1,2}")) s = s.replace(",", ".");           // 1500,50
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Monto inválido «" + fmt.formatCellValue(c).trim() + "» en " + nombreColumna + ".");
        }
    }

    private boolean siNo(String valor) {
        if (valor == null || valor.trim().equals("-")) return false;
        // La planilla de la contadora trae el monto (113.00 / 123.00): cualquier monto mayor a cero es SI
        try {
            return new BigDecimal(valor.trim().replace(",", "")).signum() > 0;
        } catch (NumberFormatException ignorada) {
            // no es un monto: se lee como SI / NO
        }
        return switch (clave(valor)) {
            case "si", "s", "true", "x", "yes", "y" -> true;
            case "no", "n", "false" -> false;
            default -> throw new RuntimeException("Asignación familiar: escriba SI o NO (vino «" + valor + "»).");
        };
    }

    private boolean filaVacia(Row fila, DataFormatter fmt) {
        if (fila == null) return true;
        for (Cell c : fila) {
            if (!fmt.formatCellValue(c).trim().isEmpty()) return false;
        }
        return true;
    }

    /** Minúsculas, sin tildes ni símbolos: «Número_Documento» y «numero documento» son la misma clave. */
    private String clave(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
