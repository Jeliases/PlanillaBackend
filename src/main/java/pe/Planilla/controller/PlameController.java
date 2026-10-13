package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.service.PlameService;

@RestController
@RequestMapping("/api/plame")
@RequiredArgsConstructor
public class PlameController {

    private final PlameService plameService;

    @GetMapping("/descargar")
    public ResponseEntity<String> descargarArchivoPlame(@RequestParam String periodo) {
        String contenidoTxt = plameService.generarTramaRemuneraciones(periodo);

        // SUNAT exige que el nombre del archivo tenga el formato: 0601 + Año + Mes + RUC + .rem
        // Usaremos un RUC ficticio genérico (Ej: 20123456789)
        String rucEmpresa = "20123456789";
        String anio = periodo.split("-")[0];
        String mes = periodo.split("-")[1];
        String nombreArchivo = "0601" + anio + mes + rucEmpresa + ".rem";

        HttpHeaders headers = new HttpHeaders();
        // Configuramos los headers para forzar la descarga del archivo TXT
        headers.setContentDispositionFormData("attachment", nombreArchivo);
        headers.setContentType(MediaType.TEXT_PLAIN);

        return new ResponseEntity<>(contenidoTxt, headers, HttpStatus.OK);
    }
}