package pe.Planilla.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.service.PlameService;

@RestController
@RequestMapping("/api/plame")
@RequiredArgsConstructor
public class PlameController {

    private final PlameService plameService;
    private final PlanillaProperties props;

    @GetMapping("/descargar")
    public ResponseEntity<String> descargarArchivoPlame(@RequestParam String periodo) {
        String contenidoTxt = plameService.generarTramaRemuneraciones(periodo);

        // SUNAT exige que el nombre del archivo tenga el formato: 0601 + Año + Mes + RUC + .rem
        // El RUC sale de app.planilla.ruc (variable de entorno EMPRESA_RUC); por defecto es uno ficticio.
        String rucEmpresa = props.getRuc();
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