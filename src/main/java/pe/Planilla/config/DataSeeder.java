package pe.Planilla.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.model.entity.ConceptoRemunerativo;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.model.entity.Usuario;
import pe.Planilla.repository.AfpRepository;
import pe.Planilla.repository.ConceptoRemunerativoRepository;
import pe.Planilla.repository.RegimenLaboralRepository;
import pe.Planilla.repository.UsuarioRepository;

import java.math.BigDecimal;

/**
 * Datos mínimos para que el sistema funcione desde el primer arranque:
 * regímenes laborales, AFPs, los conceptos que el código usa por código SUNAT y el primer usuario ADMIN.
 * Es idempotente: solo inserta lo que falta, nunca pisa datos existentes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final RegimenLaboralRepository regimenRepository;
    private final ConceptoRemunerativoRepository conceptoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AfpRepository afpRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-username}")
    private String adminUsername;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        // El nombre "Cuarta Categoría (RxH)" se compara tal cual en PlanillaServiceImpl.
        sembrarRegimen("Régimen General", 30, "1.00");
        sembrarRegimen("Pequeña Empresa (Mype)", 15, "0.50");
        sembrarRegimen("Microempresa (Remype)", 15, "0.00");
        sembrarRegimen("Cuarta Categoría (RxH)", 0, "0.00");

        // Las primeras versiones sembraron códigos que en la Tabla 22 de SUNAT significan otra cosa
        // (0101 = alimentación, 0201 = asignación familiar). Se corrigen conservando los movimientos ya registrados.
        migrarCodigo("0101", "Sueldo básico", "0121", "Remuneración o jornal básico");
        migrarCodigo("0201", "Horas extras", "0105", "Horas extras 25%");

        // Códigos de la Tabla 22 de SUNAT (PLAME) que el código fuente busca explícitamente
        sembrarConcepto("0121", "Remuneración o jornal básico", "INGRESO", true, true);
        sembrarConcepto("0105", "Horas extras 25%", "INGRESO", true, true);
        sembrarConcepto("0106", "Horas extras 35%", "INGRESO", true, true);
        sembrarConcepto("0118", "Remuneración vacacional", "INGRESO", true, true);

        // Tasas AFP vigentes según la SBS (ago-2026). Cambian: actualízalas con PUT /api/afps/{id}.
        // 12732.70 es el tope trimestral oct-dic 2026 según fuente secundaria: VERIFICAR en sbs.gob.pe.
        sembrarAfp("Habitat", "0.0147");
        sembrarAfp("Integra", "0.0155");
        sembrarAfp("Prima", "0.0160");
        sembrarAfp("Profuturo", "0.0169");

        if (usuarioRepository.count() == 0) {
            Usuario admin = new Usuario();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRol("ADMIN");
            usuarioRepository.save(admin);
            log.warn("Se creó el usuario inicial '{}'. Cambia ADMIN_PASSWORD antes de usar esto fuera de desarrollo.", adminUsername);
        }
    }

    private void sembrarRegimen(String nombre, int diasVacaciones, String factorLiquidacion) {
        boolean existe = regimenRepository.findAll().stream().anyMatch(r -> nombre.equals(r.getNombre()));
        if (existe) return;
        RegimenLaboral r = new RegimenLaboral();
        r.setNombre(nombre);
        r.setDiasVacaciones(diasVacaciones);
        r.setFactorLiquidacion(new BigDecimal(factorLiquidacion));
        regimenRepository.save(r);
    }

    private void sembrarConcepto(String codigo, String descripcion, String tipo, boolean afectaRenta5ta, boolean afectaEssalud) {
        if (conceptoRepository.findByCodigo(codigo).isPresent()) return;
        ConceptoRemunerativo c = new ConceptoRemunerativo();
        c.setCodigo(codigo);
        c.setDescripcion(descripcion);
        c.setTipo(tipo);
        c.setAfectaRenta5ta(afectaRenta5ta);
        c.setAfectaEssalud(afectaEssalud);
        conceptoRepository.save(c);
    }

    private void migrarCodigo(String codigoViejo, String descripcionVieja, String codigoNuevo, String descripcionNueva) {
        if (conceptoRepository.findByCodigo(codigoNuevo).isPresent()) return;
        conceptoRepository.findByCodigo(codigoViejo)
                .filter(c -> descripcionVieja.equals(c.getDescripcion()))
                .ifPresent(c -> {
                    c.setCodigo(codigoNuevo);
                    c.setDescripcion(descripcionNueva);
                    conceptoRepository.save(c);
                    log.info("Concepto {} migrado al código SUNAT {}.", codigoViejo, codigoNuevo);
                });
    }

    private void sembrarAfp(String nombre, String comisionFlujo) {
        if (afpRepository.findByNombre(nombre).isPresent()) return;
        Afp afp = new Afp();
        afp.setNombre(nombre);
        afp.setAporteObligatorio(new BigDecimal("0.1000"));
        afp.setComisionFlujo(new BigDecimal(comisionFlujo));
        afp.setPrimaSeguro(new BigDecimal("0.0137"));
        afp.setRemuneracionMaximaAsegurable(new BigDecimal("12732.70"));
        afpRepository.save(afp);
    }
}
