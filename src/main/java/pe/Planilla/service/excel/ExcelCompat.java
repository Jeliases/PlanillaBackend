package pe.Planilla.service.excel;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Abre Excel de cualquier origen. El .xls que descarga el marcador biométrico trae registros de hipervínculos mal
 * formados y el lector estándar (Apache POI) se cae con «Bad requested string length». En ese caso se neutralizan esos
 * registros (no se usan para nada) y se vuelve a abrir: los datos de las hojas quedan intactos.
 */
public final class ExcelCompat {
    private ExcelCompat() {}

    /** Id de registro BIFF del hipervínculo (HLINK). */
    private static final int HLINK = 0x01B8;
    /** Id que POI trata como registro desconocido y simplemente ignora. */
    private static final int DESCONOCIDO = 0x0FFE;

    public static Workbook abrir(MultipartFile archivo) throws IOException {
        byte[] bytes = archivo.getBytes();
        try {
            return WorkbookFactory.create(new ByteArrayInputStream(bytes));
        } catch (IllegalArgumentException original) {
            try {
                return new HSSFWorkbook(new ByteArrayInputStream(neutralizarHipervinculos(bytes)));
            } catch (Exception ignorada) {
                throw new RuntimeException("No se pudo leer el Excel (" + original.getMessage()
                        + "). Ábralo en Excel y guárdelo como .xlsx, y vuelva a subirlo.");
            }
        }
    }

    static byte[] neutralizarHipervinculos(byte[] xls) throws IOException {
        POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(xls));
        String nombre = fs.getRoot().hasEntry("Workbook") ? "Workbook" : "Book";
        DocumentEntry entrada = (DocumentEntry) fs.getRoot().getEntry(nombre);
        byte[] datos = new byte[entrada.getSize()];
        try (DocumentInputStream in = new DocumentInputStream(entrada)) {
            in.readFully(datos);
        }
        // Recorre los registros BIFF: 2 bytes de id, 2 de largo y los datos
        int pos = 0;
        while (pos + 4 <= datos.length) {
            int id = (datos[pos] & 0xFF) | ((datos[pos + 1] & 0xFF) << 8);
            int largo = (datos[pos + 2] & 0xFF) | ((datos[pos + 3] & 0xFF) << 8);
            if (id == HLINK) {
                datos[pos] = (byte) (DESCONOCIDO & 0xFF);
                datos[pos + 1] = (byte) (DESCONOCIDO >> 8);
            }
            pos += 4 + largo;
        }
        entrada.delete();
        fs.createDocument(new ByteArrayInputStream(datos), nombre);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        fs.writeFilesystem(salida);
        return salida.toByteArray();
    }
}
