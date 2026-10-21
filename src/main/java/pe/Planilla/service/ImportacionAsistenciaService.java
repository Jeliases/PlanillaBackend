package pe.Planilla.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.Planilla.model.dto.ImportacionArchivoDTO;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO.ErrorFila;
import pe.Planilla.model.entity.AsistenciaDiaria;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.HorarioProgramado;
import pe.Planilla.model.entity.ReglaTardanza;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.*;
import pe.Planilla.service.calculo.CalculadoraAsistencia;
import pe.Planilla.service.calculo.CalculadoraAsistencia.Marcas;
import pe.Planilla.service.calculo.CalculadoraAsistencia.ResultadoDia;
import pe.Planilla.service.excel.ExcelCompat;
import pe.Planilla.service.excel.ExcelLector;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa el Excel que descarga el marcador biométrico (hoja «Attendance Record»): una fila por trabajador y una columna
 * por día, con las marcaciones de cada día en la celda (ingreso, almuerzo inicio, almuerzo fin, salida laboral).
 *
 * El ID del reloj debe ser el documento (DNI/CE) del trabajador. Cada día se compara con su horario programado (tareo) o
 * con el horario por defecto, y se registra asistencia, tardanza o falta. Los feriados y los días de descanso no generan falta.
 */
@Service
@RequiredArgsConstructor
public class ImportacionAsistenciaService {

    private static final Pattern RANGO = Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s*-\\s*(\\d{4})/(\\d{1,2})/(\\d{1,2})");
    private static final Pattern HORA = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private final EmpleadoRepository empleadoRepository;
    private final AsistenciaDiariaRepository asistenciaRepository;
    private final HorarioProgramadoRepository horarioRepository;
    private final FeriadoRepository feriadoRepository;
    private final ReglaTardanzaRepository reglaRepository;
    private final CalculadoraAsistencia calculadora;
    private final NotificadorTiempoReal notificador;

    private static final class Acumulado {
        String nombre;
        int tardanzas, faltas, minutosTardanza;
    }

    @Transactional
    public ImportacionArchivoDTO importar(MultipartFile archivo, boolean soloValidar) {
        if (archivo == null || archivo.isEmpty()) {
            throw new RuntimeException("Seleccione el Excel descargado del marcador (.xls o .xlsx).");
        }
        String nombre = Optional.ofNullable(archivo.getOriginalFilename()).orElse("").toLowerCase();
        if (!nombre.endsWith(".xls") && !nombre.endsWith(".xlsx")) {
            throw new RuntimeException("El archivo debe ser el Excel del marcador (.xls o .xlsx).");
        }

        List<ErrorFila> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        List<AsistenciaDiaria> aGuardar = new ArrayList<>();
        Set<String> sinCoincidencia = new LinkedHashSet<>();
        Map<Integer, Acumulado> porEmpleado = new LinkedHashMap<>();
        int trabajadoresEnArchivo = 0, empleadosCoinciden = 0, dias = 0, tardanzas = 0, faltas = 0, incompletas = 0, minutosExtra = 0, fueraDeTabla = 0;
        LocalDate inicio, fin;

        try (Workbook libro = ExcelCompat.abrir(archivo)) {
            Sheet hoja = buscarHoja(libro);
            DataFormatter fmt = new DataFormatter();

            // --- rango de fechas: «Made Date:2026/07/01-2026/07/24»
            int filaCabecera = -1;
            LocalDate ini = null, finRango = null;
            for (int i = 0; i <= Math.min(hoja.getLastRowNum(), 15); i++) {
                Row r = hoja.getRow(i);
                if (r == null) continue;
                String a = ExcelLector.texto(r.getCell(0), fmt);
                if (a == null) continue;
                Matcher m = RANGO.matcher(a);
                if (a.toLowerCase().contains("made date") && m.find()) {
                    ini = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                    finRango = LocalDate.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
                }
                if (ExcelLector.clave(a).equals("employeeid")) {
                    filaCabecera = i;
                    break;
                }
            }
            if (filaCabecera < 0) throw new RuntimeException("No se encontró la fila de encabezados («Employee ID»). ¿Es el Excel del marcador?");
            if (ini == null) throw new RuntimeException("No se encontró el rango de fechas («Made Date: aaaa/mm/dd-aaaa/mm/dd») en la hoja.");
            inicio = ini;
            fin = finRango;

            // --- columnas de días: la primera es D (índice 3) y cada una es un día seguido
            Row cab = hoja.getRow(filaCabecera);
            int primeraDia = 3;
            int ultimaDia = primeraDia - 1;
            for (int c = primeraDia; c < cab.getLastCellNum(); c++) {
                String h = ExcelLector.texto(cab.getCell(c), fmt);
                if (h == null || !h.matches("\\d{1,2}")) break;
                LocalDate fechaCol = inicio.plusDays(c - primeraDia);
                if (Integer.parseInt(h) != fechaCol.getDayOfMonth()) {
                    throw new RuntimeException("La columna del día «" + h + "» no coincide con la fecha esperada " + fechaCol
                            + ". El archivo debe tener días consecutivos.");
                }
                ultimaDia = c;
            }
            if (ultimaDia < primeraDia) throw new RuntimeException("No se encontraron columnas de días en el archivo.");
            LocalDate ultimaFecha = inicio.plusDays(ultimaDia - primeraDia);

            // --- datos de apoyo del periodo
            // El ID del reloj se busca primero en «ID del marcador» del empleado y, si no, en su documento
            Map<String, Empleado> porDocumento = new HashMap<>();
            Map<String, Empleado> porMarcador = new HashMap<>();
            for (Empleado e : empleadoRepository.findAll()) {
                porDocumento.putIfAbsent(e.getDni(), e);
                if (e.getIdMarcador() != null && !e.getIdMarcador().isBlank()) porMarcador.putIfAbsent(e.getIdMarcador().trim(), e);
            }
            Map<String, HorarioProgramado> horarios = new HashMap<>();
            horarioRepository.findByFechaBetween(inicio, ultimaFecha)
                    .forEach(h -> horarios.put(h.getEmpleado().getIdEmpleado() + "|" + h.getFecha(), h));
            Set<LocalDate> feriados = new HashSet<>();
            feriadoRepository.findByFechaBetweenOrderByFecha(inicio, ultimaFecha).forEach(f -> feriados.add(f.getFecha()));
            Map<String, AsistenciaDiaria> existentes = new HashMap<>();
            asistenciaRepository.findByFechaBetween(inicio, ultimaFecha)
                    .forEach(a -> existentes.put(a.getEmpleado().getIdEmpleado() + "|" + a.getFecha(), a));
            List<ReglaTardanza> reglas = reglaRepository.findAllByOrderByDesdeMinutoAsc();
            Integer topeTabla = reglas.stream().map(ReglaTardanza::getHastaMinuto).filter(Objects::nonNull).max(Integer::compare).orElse(null);

            // --- una fila por trabajador
            for (int i = filaCabecera + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (ExcelLector.filaVacia(fila, fmt)) continue;
                String idReloj = ExcelLector.textoNumerico(fila.getCell(0), fmt);
                if (idReloj == null) continue;
                trabajadoresEnArchivo++;

                Empleado emp = buscarEmpleado(porMarcador, porDocumento, idReloj);
                if (emp == null) {
                    // Se muestra también el nombre que tiene el reloj para que se pueda reconocer a la persona
                    String nombreReloj = ExcelLector.texto(fila.getCell(1), fmt);
                    sinCoincidencia.add(idReloj + (nombreReloj != null ? " (" + nombreReloj + ")" : ""));
                    continue;
                }
                empleadosCoinciden++;
                Acumulado acu = porEmpleado.computeIfAbsent(emp.getIdEmpleado(), k -> new Acumulado());
                acu.nombre = emp.getNombres() + " " + emp.getApellidos();

                for (int c = primeraDia; c <= ultimaDia; c++) {
                    LocalDate fecha = inicio.plusDays(c - primeraDia);
                    if (emp.getFechaIngreso() != null && fecha.isBefore(emp.getFechaIngreso())) continue; // aún no trabajaba

                    List<LocalTime> leidas = leerMarcas(ExcelLector.texto(fila.getCell(c), fmt), i + 1, fecha, errores);
                    Marcas marcas = calculadora.distribuir(leidas);
                    HorarioProgramado prog = horarios.get(emp.getIdEmpleado() + "|" + fecha);
                    boolean laborable = calculadora.esLaborable(fecha, prog, feriados.contains(fecha));
                    LocalTime entradaProg = calculadora.entradaProgramada(prog);
                    LocalTime salidaProg = calculadora.salidaProgramada(prog);

                    ResultadoDia res = calculadora.evaluar(marcas, entradaProg, salidaProg, laborable);
                    if (res == null) continue;

                    AsistenciaDiaria existente = existentes.get(emp.getIdEmpleado() + "|" + fecha);
                    // Un permiso o descanso médico digitado a mano no se pisa con una "falta" del reloj
                    if (existente != null && res.estado().equals("FALTA")
                            && ("PERMISO".equals(existente.getEstado()) || "DESCANSO_MEDICO".equals(existente.getEstado()))) {
                        continue;
                    }
                    AsistenciaDiaria a = existente != null ? existente : new AsistenciaDiaria();
                    a.setEmpleado(emp);
                    a.setFecha(fecha);
                    a.setEstado(res.estado());
                    a.setMinutosTardanza(res.minutosTardanza());
                    a.setMinutosExtra(res.minutosExtra());
                    a.setMarcaIngreso(marcas.ingreso());
                    a.setMarcaAlmuerzoInicio(marcas.almuerzoInicio());
                    a.setMarcaAlmuerzoFin(marcas.almuerzoFin());
                    a.setMarcaSalida(marcas.salida());
                    a.setHoraIngreso(marcas.ingreso());
                    a.setHoraSalida(marcas.salida());
                    a.setHoraProgramadaEntrada(laborable ? entradaProg : null);
                    a.setHoraProgramadaSalida(laborable ? salidaProg : null);
                    a.setObservacion(res.observacion());
                    a.setOrigen("MARCADOR");
                    aGuardar.add(a);

                    dias++;
                    minutosExtra += res.minutosExtra();
                    if (marcas.incompleta()) incompletas++;
                    if (res.estado().equals("TARDANZA")) {
                        tardanzas++;
                        acu.tardanzas++;
                        acu.minutosTardanza += res.minutosTardanza();
                        if (topeTabla != null && res.minutosTardanza() > topeTabla) fueraDeTabla++;
                    } else if (res.estado().equals("FALTA")) {
                        faltas++;
                        acu.faltas++;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage());
        }

        if (!sinCoincidencia.isEmpty()) {
            advertencias.add(sinCoincidencia.size() + " ID del reloj no coinciden con ningún empleado registrado (se ignoran). "
                    + "Si son trabajadores, regístralos con ese número como documento, o escribe ese ID en «ID en el marcador» de su ficha.");
        }
        if (fueraDeTabla > 0) {
            advertencias.add(fueraDeTabla + " tardanza(s) superan el último tramo de la tabla de descuentos: se les aplica el último tramo. Confirma la regla.");
        }
        if (incompletas > 0) {
            advertencias.add(incompletas + " día(s) con marcaciones incompletas o de más: revisa la columna de observaciones.");
        }
        if (faltas > 0) {
            advertencias.add(faltas + " falta(s) en días laborables sin marcación. Si hubo permiso, descanso médico o es un horario de descanso, justifícalas o carga el horario.");
        }

        boolean importar = !soloValidar && errores.isEmpty();
        if (importar) {
            asistenciaRepository.saveAll(aGuardar);
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("desde", inicio.toString());
        resumen.put("hasta", fin.toString());
        resumen.put("trabajadoresEnArchivo", trabajadoresEnArchivo);
        resumen.put("empleadosConCoincidencia", empleadosCoinciden);
        resumen.put("sinCoincidencia", new ArrayList<>(sinCoincidencia));
        resumen.put("diasRegistrados", dias);
        resumen.put("tardanzas", tardanzas);
        resumen.put("faltas", faltas);
        resumen.put("marcacionesIncompletas", incompletas);
        resumen.put("minutosExtra", minutosExtra);
        List<Map<String, Object>> detalle = new ArrayList<>();
        porEmpleado.values().stream().filter(a -> a.tardanzas > 0 || a.faltas > 0).forEach(a -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("empleado", a.nombre);
            f.put("tardanzas", a.tardanzas);
            f.put("minutosTardanza", a.minutosTardanza);
            f.put("faltas", a.faltas);
            detalle.add(f);
        });
        resumen.put("detalle", detalle);

        if (importar) {
            // Un solo aviso para que las pantallas abiertas recarguen el tareo
            notificador.publicar("asistencias", "IMPORTADO", null, resumen);
        }
        return new ImportacionArchivoDTO(trabajadoresEnArchivo, empleadosCoinciden, importar, errores, advertencias, resumen);
    }

    // ----------------------------------------------------------------- ayudas

    private Sheet buscarHoja(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            if (ExcelLector.clave(libro.getSheetName(i)).equals("attendancerecord")) return libro.getSheetAt(i);
        }
        throw new RuntimeException("No se encontró la hoja «Attendance Record». Suba el Excel tal como lo descarga el marcador.");
    }

    /** Primero por «ID del marcador»; si no, el ID del reloj es el documento (si perdió ceros a la izquierda se completa a 8 dígitos). */
    private Empleado buscarEmpleado(Map<String, Empleado> porMarcador, Map<String, Empleado> porDocumento, String idReloj) {
        String id = idReloj.trim();
        Empleado e = porMarcador.get(id);
        if (e != null) return e;
        e = porDocumento.get(id);
        if (e == null && id.matches("\\d{1,7}")) e = porDocumento.get(String.format("%08d", Long.parseLong(id)));
        return e;
    }

    private List<LocalTime> leerMarcas(String celda, int numeroFila, LocalDate fecha, List<ErrorFila> errores) {
        List<LocalTime> marcas = new ArrayList<>();
        if (celda == null) return marcas;
        Matcher m = HORA.matcher(celda);
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h > 23 || min > 59) {
                errores.add(new ErrorFila(numeroFila, "Hora inválida «" + m.group() + "» el " + fecha + "."));
                continue;
            }
            marcas.add(LocalTime.of(h, min));
        }
        return marcas;
    }
}
