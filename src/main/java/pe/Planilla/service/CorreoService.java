package pe.Planilla.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.config.PlanillaProperties;
import pe.Planilla.model.dto.BoletaGeneradaDTO;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.repository.EmpleadoRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envía la boleta en PDF al correo del trabajador. Necesita MAIL_HOST/MAIL_USER/MAIL_PASSWORD y MAIL_HABILITADO=true;
 * sin eso responde con un aviso claro en vez de fallar.
 */
@Service
@RequiredArgsConstructor
public class CorreoService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final BoletaPdfService pdfService;
    private final PlanillaService planillaService;
    private final EmpleadoRepository empleadoRepository;
    private final PlanillaProperties props;

    @Value("${app.correo.habilitado}")
    private boolean habilitado;

    @Value("${app.correo.remitente}")
    private String remitente;

    @Value("${spring.mail.host}")
    private String host;

    public boolean estaHabilitado() {
        return habilitado && host != null && !host.isBlank() && mailSender.getIfAvailable() != null;
    }

    /** Envía las boletas del periodo. Devuelve cuántas se enviaron y a quiénes no se pudo (sin correo o error). */
    @Transactional(readOnly = true)
    public Map<String, Object> enviarPeriodo(String periodo, Integer soloIdBoleta) {
        if (!estaHabilitado()) {
            throw new RuntimeException("El envío de correos no está configurado. Defina MAIL_HOST, MAIL_USER, MAIL_PASSWORD y MAIL_HABILITADO=true en el servidor.");
        }
        List<BoletaGeneradaDTO> boletas = soloIdBoleta != null
                ? List.of(planillaService.obtenerBoleta(soloIdBoleta))
                : planillaService.listarBoletas(periodo);
        if (boletas.isEmpty()) {
            throw new RuntimeException("No hay boletas procesadas para el periodo " + periodo + ".");
        }

        int enviadas = 0;
        List<String> sinCorreo = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        JavaMailSender sender = mailSender.getObject();
        for (BoletaGeneradaDTO b : boletas) {
            Empleado e = empleadoRepository.findById(b.getIdEmpleado()).orElse(null);
            if (e == null || e.getCorreo() == null || e.getCorreo().isBlank()) {
                sinCorreo.add(b.getNombresEmpleado());
                continue;
            }
            try {
                byte[] pdf = pdfService.generar(b.getIdBoleta());
                YearMonth ym = YearMonth.parse(b.getPeriodo());
                MimeMessage msg = sender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
                h.setFrom(remitente);
                h.setTo(e.getCorreo());
                h.setSubject("Boleta de pago " + ym.getMonthValue() + "/" + ym.getYear() + " - " + props.getRazonSocial());
                h.setText("Estimado(a) " + e.getNombres() + ",\n\nAdjuntamos su boleta de pago del periodo " + b.getPeriodo()
                        + ".\n\nEste correo se genera automáticamente; si tiene alguna consulta comuníquese con el área de planillas.\n\n"
                        + props.getRazonSocial());
                h.addAttachment(pdfService.nombreArchivo(b), new ByteArrayResource(pdf));
                sender.send(msg);
                enviadas++;
            } catch (Exception ex) {
                errores.add(b.getNombresEmpleado() + ": " + ex.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enviadas", enviadas);
        r.put("sinCorreo", sinCorreo);
        r.put("errores", errores);
        return r;
    }
}
