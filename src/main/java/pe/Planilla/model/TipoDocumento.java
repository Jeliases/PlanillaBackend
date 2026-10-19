package pe.Planilla.model;

import java.text.Normalizer;

/**
 * Tipos de documento de identidad del trabajador. Por decisión de la empresa solo se admiten DNI peruano y
 * carné de extranjería. El código SUNAT sale en el archivo PLAME (01 DNI, 04 CE según la especificación de
 * planillas electrónicas de SUNAT; confirmar con la Tabla 2 del T-Registro).
 */
public enum TipoDocumento {
    DNI("01", "DNI"),
    CE("04", "Carné de extranjería");

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
            default -> throw new RuntimeException("Tipo de documento no válido: «" + texto.trim() + "». Use DNI o CE.");
        };
    }
}
