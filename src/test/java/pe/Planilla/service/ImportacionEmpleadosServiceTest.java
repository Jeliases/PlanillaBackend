package pe.Planilla.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pe.Planilla.model.TipoDocumento;
import pe.Planilla.model.dto.ImportacionEmpleadosDTO;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.AfpRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.RegimenLaboralRepository;
import pe.Planilla.service.impl.EmpleadoServiceImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Prueba la carga masiva con un Excel real armado en la prueba y repositorios simulados (sin base de datos ni servidor). */
class ImportacionEmpleadosServiceTest {

    private static final String[] COLS = {"tipo_documento", "numero_documento", "nombres", "apellidos", "fecha_nacimiento",
            "fecha_ingreso", "cargo", "area", "correo", "telefono", "sueldo_base", "regimen", "sistema_pensionario", "afp",
            "cuspp", "tipo_comision_afp", "asignacion_familiar", "banco", "cuenta_bancaria", "banco_cts", "cuenta_cts", "id_marcador"};

    private EmpleadoRepository empleadoRepo;
    private ImportacionEmpleadosService servicio;

    @BeforeEach
    void preparar() {
        RegimenLaboral general = regimen(1, "Régimen General");
        RegimenLaboral micro = regimen(3, "Microempresa (Remype)");
        Afp habitat = new Afp();
        habitat.setIdAfp(1);
        habitat.setNombre("Habitat");

        RegimenLaboralRepository regimenRepo = mock(RegimenLaboralRepository.class);
        when(regimenRepo.findAll()).thenReturn(List.of(general, micro));
        when(regimenRepo.findById(1)).thenReturn(Optional.of(general));
        when(regimenRepo.findById(3)).thenReturn(Optional.of(micro));

        AfpRepository afpRepo = mock(AfpRepository.class);
        when(afpRepo.findAll()).thenReturn(List.of(habitat));
        when(afpRepo.findById(1)).thenReturn(Optional.of(habitat));

        empleadoRepo = mock(EmpleadoRepository.class);
        when(empleadoRepo.findAllByDni(anyString())).thenReturn(Collections.emptyList());
        when(empleadoRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        EmpleadoServiceImpl empleadoService = new EmpleadoServiceImpl(empleadoRepo, regimenRepo, afpRepo, mock(NotificadorTiempoReal.class));
        servicio = new ImportacionEmpleadosService(empleadoService, regimenRepo, afpRepo);
    }

    private RegimenLaboral regimen(int id, String nombre) {
        RegimenLaboral r = new RegimenLaboral();
        r.setIdRegimen(id);
        r.setNombre(nombre);
        r.setDiasVacaciones(30);
        r.setFactorLiquidacion(BigDecimal.ONE);
        return r;
    }

    /** Arma un .xlsx: cada fila es un arreglo con un valor por columna de COLS (String, Number, LocalDate o null). */
    private MockMultipartFile excel(String nombreArchivo, Object[]... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Empleados");
            Row cab = hoja.createRow(0);
            for (int i = 0; i < COLS.length; i++) cab.createCell(i).setCellValue(COLS[i]);
            CellStyle estiloFecha = libro.createCellStyle();
            estiloFecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
            int r = 1;
            for (Object[] datos : filas) {
                Row fila = hoja.createRow(r++);
                for (int c = 0; c < datos.length; c++) {
                    Object v = datos[c];
                    if (v == null) continue;
                    Cell cel = fila.createCell(c);
                    if (v instanceof LocalDate f) {
                        cel.setCellValue(java.sql.Date.valueOf(f));
                        cel.setCellStyle(estiloFecha);
                    } else if (v instanceof Number n) {
                        cel.setCellValue(n.doubleValue());
                    } else {
                        cel.setCellValue(v.toString());
                    }
                }
            }
            libro.write(out);
            return new MockMultipartFile("archivo", nombreArchivo, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    // tipo, numero, nombres, apellidos, nacimiento, ingreso, cargo, area, correo, telefono, sueldo, regimen, sistema, afp, cuspp, asig, banco, cuenta, bancoCts, cuentaCts
    private Object[] fila(Object tipo, Object numero, Object ingreso, Object sueldo, Object regimen, Object sistema, Object afp) {
        return new Object[]{tipo, numero, "Nombre", "Apellido", null, ingreso, "Asesor", null, null, null, sueldo, regimen, sistema, afp, null, null, "SI", null, null, null, null};
    }

    // ------------------------------------------------------------------ plantilla

    @Test
    void laPlantillaTieneHojasEncabezadosYDesplegables() throws IOException {
        byte[] bytes = servicio.generarPlantilla();
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertNotNull(libro.getSheet("Empleados"));
            assertNotNull(libro.getSheet("Instrucciones"));
            assertNotNull(libro.getSheet("Listas"));
            Row cab = libro.getSheet("Empleados").getRow(0);
            assertEquals(COLS.length, cab.getLastCellNum());
            assertEquals("numero_documento", cab.getCell(1).getStringCellValue());
            assertEquals(6, libro.getSheet("Empleados").getDataValidations().size()); // tipo doc, régimen, AFP, sistema, asig. familiar, comisión
            assertEquals("Régimen General", libro.getSheet("Listas").getRow(1).getCell(1).getStringCellValue());
            // sin fila de ejemplo: nadie debe importar datos falsos por descuido
            assertEquals(0, libro.getSheet("Empleados").getLastRowNum());
        }
    }

    // ----------------------------------------------------------------- importar

    @Test
    void importaFilasCorrectasYRecuperaCerosYFormatos() throws IOException {
        ImportacionEmpleadosDTO r = servicio.importar(excel("ok.xlsx",
                // DNI escrito como número: Excel le quitó el cero inicial; AFP con asignación familiar
                fila("DNI", 1234567, LocalDate.of(2025, 3, 1), 1800, "Régimen General", "AFP", "Habitat"),
                // Carné de extranjería, fecha como texto, sueldo con "S/" y miles, régimen sin tildes ni mayúsculas
                fila("Carné de extranjería", "004455667", "15/06/2025", "S/ 2,500.50", "microempresa (remype)", null, null)), false);

        assertTrue(r.getErrores().isEmpty(), r.getErrores().toString());
        assertTrue(r.isImportado());
        assertEquals(2, r.getEmpleados().size());

        var uno = r.getEmpleados().get(0);
        assertEquals("01234567", uno.getDni());
        assertEquals("DNI", uno.getTipoDocumento());
        assertEquals("AFP", uno.getSistemaPensionario());
        assertEquals("Habitat", uno.getAfp());
        assertTrue(uno.getAsignacionFamiliar());
        assertEquals(LocalDate.of(2025, 3, 1), uno.getFechaIngreso());

        var dos = r.getEmpleados().get(1);
        assertEquals("CE", dos.getTipoDocumento());
        assertEquals(LocalDate.of(2025, 6, 15), dos.getFechaIngreso());
        assertEquals(0, new BigDecimal("2500.50").compareTo(dos.getSueldoBase()));
        assertEquals("ONP", dos.getSistemaPensionario());

        verify(empleadoRepo, times(1)).saveAll(anyList());
    }

    @Test
    void siHayUnSoloErrorNoSeGuardaNada() throws IOException {
        Empleado existente = new Empleado();
        existente.setDni("99999999"); // empleado antiguo sin tipo de documento = DNI
        when(empleadoRepo.findAllByDni("99999999")).thenReturn(List.of(existente));

        ImportacionEmpleadosDTO r = servicio.importar(excel("errores.xlsx",
                fila("DNI", "45871236", LocalDate.of(2025, 3, 1), 1800, "Régimen General", null, null),       // fila 2: correcta
                fila("DNI", "45871236", LocalDate.of(2025, 3, 1), 1800, "Régimen General", null, null),       // fila 3: repetida en el archivo
                fila("DNI", "11111111", LocalDate.of(2025, 3, 1), 1800, "Inexistente", null, null),           // fila 4: régimen
                fila("DNI", "12345", LocalDate.of(2025, 3, 1), 1800, "Régimen General", null, null),          // fila 5: DNI corto
                fila("DNI", "22222222", LocalDate.of(2025, 3, 1), -5, "Régimen General", null, null),         // fila 6: sueldo
                fila("DNI", "33333333", "31/02/2025", 1800, "Régimen General", null, null),                   // fila 7: fecha
                fila("DNI", "44444444", LocalDate.of(2025, 3, 1), 1800, "Régimen General", "AFP", null),      // fila 8: AFP sin nombre
                fila("DNI", "99999999", LocalDate.of(2025, 3, 1), 1800, "Régimen General", null, null),       // fila 9: ya existe en la base
                fila("Libreta", "55555555", LocalDate.of(2025, 3, 1), 1800, "Régimen General", null, null)),  // fila 10: tipo de documento
                false);

        assertFalse(r.isImportado());
        assertEquals(9, r.getTotalFilas());
        assertEquals(1, r.getFilasValidas());
        assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10), r.getErrores().stream().map(ImportacionEmpleadosDTO.ErrorFila::getFila).toList());
        assertTrue(r.getErrores().get(0).getMensaje().contains("repetido"));
        assertTrue(r.getErrores().get(1).getMensaje().contains("no existe"));
        assertTrue(r.getErrores().get(2).getMensaje().contains("8 dígitos"));
        assertTrue(r.getErrores().get(3).getMensaje().contains("sueldo"));
        assertTrue(r.getErrores().get(4).getMensaje().toLowerCase().contains("fecha"));
        assertTrue(r.getErrores().get(5).getMensaje().contains("AFP"));
        assertTrue(r.getErrores().get(6).getMensaje().contains("Ya existe"));
        assertTrue(r.getErrores().get(7).getMensaje().contains("Tipo de documento"));
        verify(empleadoRepo, never()).saveAll(anyList());
    }

    @Test
    void soloValidarNoGuardaAunqueTodoEsteBien() throws IOException {
        ImportacionEmpleadosDTO r = servicio.importar(excel("ok.xlsx",
                fila("DNI", "45871236", LocalDate.of(2025, 3, 1), 1800, "Régimen General", null, null)), true);
        assertTrue(r.getErrores().isEmpty());
        assertFalse(r.isImportado());
        assertEquals(1, r.getFilasValidas());
        verify(empleadoRepo, never()).saveAll(anyList());
    }

    @Test
    void sinLasColumnasObligatoriasFalla() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row cab = libro.createSheet("Empleados").createRow(0);
            cab.createCell(0).setCellValue("nombres");
            libro.write(out);
            var archivo = new MockMultipartFile("archivo", "mal.xlsx", "application/octet-stream", out.toByteArray());
            RuntimeException e = assertThrows(RuntimeException.class, () -> servicio.importar(archivo, true));
            assertTrue(e.getMessage().contains("Faltan columnas obligatorias"));
        }
    }

    @Test
    void rechazaArchivosQueNoSonExcel() {
        var archivo = new MockMultipartFile("archivo", "datos.csv", "text/csv", "a,b".getBytes());
        assertThrows(RuntimeException.class, () -> servicio.importar(archivo, true));
    }

    // ----------------------------------------------------------- tipos de documento

    @Test
    void tiposDeDocumento() {
        assertEquals(TipoDocumento.DNI, TipoDocumento.desdeTexto(null));
        assertEquals(TipoDocumento.DNI, TipoDocumento.desdeTexto(" dni "));
        assertEquals(TipoDocumento.CE, TipoDocumento.desdeTexto("Carné de Extranjería"));
        assertEquals(TipoDocumento.CE, TipoDocumento.desdeTexto("04"));
        assertEquals("04", TipoDocumento.CE.getCodigoSunat());
        assertNull(TipoDocumento.DNI.validarNumero("12345678"));
        assertNotNull(TipoDocumento.DNI.validarNumero("1234567"));
        assertNull(TipoDocumento.CE.validarNumero("A12345678"));
        assertNotNull(TipoDocumento.CE.validarNumero("12 34"));
        // La empresa solo admite DNI y carné de extranjería
        assertThrows(RuntimeException.class, () -> TipoDocumento.desdeTexto("pasaporte"));
        assertThrows(RuntimeException.class, () -> TipoDocumento.desdeTexto("libreta"));
    }
}
