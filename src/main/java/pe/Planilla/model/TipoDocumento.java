package pe.Planilla.model;

import java.text.Normalizer;

/**
 * Tipos de documento de identidad del trabajador. El código SUNAT sale en el archivo PLAME.
 * Códigos tomados de la especificación de planillas electrónicas de SUNAT (01 DNI, 04 CE, 07 Pasaporte,
 * 09 Solicitante de refugio, 23 PTP): confirmarlos contra la Tabla 2 del T-Registro vigente.
 */
public enum TipoDocumento {
    DNI("01", "DNI"),
    CE("04", "Carné de extranjería"),
    PASAPORTE("07", "Pasaporte"),
    PTP("23", "Carné PTP"),
    CSR("09", "Carné de solicitante de refugio");

    private final String codigoSunat;
    private final String etiqueta;

    TipoDocumento(String codigoSunat, String etiqueta) {
        this.codigoSunat = codigoSunat;
        this.etiqueta = etiqueta;
    }

    public String getCodigoSunat() {
        return codigoSunat;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** null si el número es válido para este tipo; si no, el mensaje que se muestra al usuario. */
    public String validarNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return "El número de documento es obligatorio.";
        }
        if (this == DNI) {
            return numero.matches("\\d{8}") ? null : "El DNI debe tener exactamente 8 dígitos.";
        }
        return numero.matches("[A-Za-z0-9]{6,15}") ? null
                : etiqueta + ": use de 6 a 15 letras o números, sin espacios ni guiones.";
    }

    /** Vacío = DNI. Acepta el nombre, el código SUNAT o variantes comunes ("Carné de extranjería", "ce", "04"...). */
    public static TipoDocumento desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) return DNI;
        String clave = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
        return switch (clave) {
            case "DNI", "01" -> DNI;
            case "CE", "04", "CARNETDEEXTRANJERIA", "CARNEDEEXTRANJERIA" -> CE;
            case "PASAPORTE", "07" -> PASAPORTE;
            case "PTP", "23", "CARNEPTP", "PERMISOTEMPORALDEPERMANENCIA" -> PTP;
            case "CSR", "09", "CARNEDESOLICITANTEDEREFUGIO" -> CSR;
            default -> throw new RuntimeException("Tipo de documento no válido: «" + texto.trim()
                    + "». Use DNI, CE, PASAPORTE, PTP o CSR.");
        };
    }
}
