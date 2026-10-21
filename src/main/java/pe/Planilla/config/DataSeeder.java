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
import pe.Planilla.model.entity.Feriado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.model.entity.ReglaTardanza;
import pe.Planilla.model.entity.Usuario;
import pe.Planilla.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    private final ReglaTardanzaRepository reglaTardanzaRepository;
    private final FeriadoRepository feriadoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-username}")
    private String adminUsername;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        // El nombre "Cuarta Categoría (RxH)" se compara tal cual en PlanillaServiceImpl.
        // Asignación familiar: solo el Régimen General (respuesta de la empresa: Mype y Micro no la pagan)
        sembrarRegimen("Régimen General", 30, "1.00", true);
        sembrarRegimen("Pequeña Empresa (Mype)", 15, "0.50", false);
        sembrarRegimen("Microempresa (Remype)", 15, "0.00", false);
        sembrarRegimen("Cuarta Categoría (RxH)", 0, "0.00", false);

        sembrarReglasTardanza();
        sembrarFeriados2026();

        // Las primeras versiones sembraron códigos que en la Tabla 22 de SUNAT significan otra cosa
        // (0101 = alimentación, 0201 = asignación familiar). Se corrigen conservando los movimientos ya registrados.
        migrarCodigo("0101", "Sueldo básico", "0121", "Remuneración o jornal básico");
        migrarCodigo("0201", "Horas extras", "0105", "Horas extras 25%");

        // Códigos de la Tabla 22 de SUNAT (PLAME) que el código fuente busca explícitamente
        sembrarConcepto("0121", "Remuneración o jornal básico", "INGRESO", true, true);
        sembrarConcepto("0105", "Horas extras 25%", "INGRESO", true, true);
        sembrarConcepto("0106", "Horas extras 35%", "INGRESO", true, true);
        sembrarConcepto("0118", "Remuneración vacacional", "INGRESO", true, true);
        // Conceptos de la planilla de la contadora (2026-10-21). Los códigos 0102/0107/0904/0915 son de la Tabla 22:
        // confirmarlos con el contador antes de declarar. Los "IP.." son internos (no salen en el PLAME).
        sembrarConcepto("0102", "Comisiones", "INGRESO", true, true);
        sembrarConcepto("0107", "Trabajo en día feriado o de descanso", "INGRESO", true, true);
        sembrarConcepto("0904", "Movilidad (condición de trabajo, no remunerativa)", "INGRESO", false, false);
        sembrarConcepto("0915", "Capacitación (condición de trabajo, no remunerativa)", "INGRESO", false, false);
        sembrarConcepto("IP01", "Bono por condición de trabajo (no remunerativo)", "INGRESO", false, false);
        sembrarConcepto("0701", "Adelanto de remuneración (1ra quincena)", "DESCUENTO", false, false);
        sembrarConcepto("0702", "Cuota de préstamo", "DESCUENTO", false, false);
        sembrarConcepto("IP11", "Descuento por menú", "DESCUENTO", false, false);
        sembrarConcepto("IP12", "Penalidad", "DESCUENTO", false, false);
        sembrarConcepto("IP13", "Equipos (SIP)", "DESCUENTO", false, false);
        sembrarConcepto("IP14", "EPS: aporte del trabajador", "DESCUENTO", false, false);
        sembrarConcepto("IP15", "Otros descuentos", "DESCUENTO", false, false);

        // Tasas AFP vigentes según la SBS (ago-2026). Cambian: actualízalas con PUT /api/afps/{id}.
        // 12732.70 es el tope trimestral oct-dic 2026 según fuente secundaria: VERIFICAR en sbs.gob.pe.
        sembrarAfp("Habitat", "0.0147");
        sembrarAfp("Integra", "0.0155");
        sembrarAfp("Prima", "0.0160");
        sembrarAfp("Profuturo", "0.0169");

        // Bases creadas antes de existir la columna "activo": los usuarios quedaron en falso y nadie podría entrar
        java.util.List<Usuario> usuarios = usuarioRepository.findAll();
        if (!usuarios.isEmpty() && usuarios.stream().noneMatch(Usuario::isActivo)) {
            usuarios.forEach(u -> {
                u.setActivo(true);
                if (u.getRol() == null || u.getRol().isBlank()) u.setRol(Usuario.ROL_ADMIN);
            });
            usuarioRepository.saveAll(usuarios);
            log.warn("Se activaron los usuarios existentes (migración de la columna 'activo').");
        }

        if (usuarioRepository.count() == 0 && (adminPassword == null || adminPassword.isBlank())) {
            log.error("No hay usuarios y ADMIN_PASSWORD esta vacia: no se crea el usuario inicial. Defina ADMIN_PASSWORD y reinicie.");
        } else if (usuarioRepository.count() == 0) {
            Usuario admin = new Usuario();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRol(Usuario.ROL_ADMIN);
            admin.setNombreCompleto("Administrador");
            admin.setActivo(true);
            admin.setDebeCambiarPassword(true); // obliga a cambiar la contraseña inicial al primer ingreso
            usuarioRepository.save(admin);
            log.warn("Se creó el usuario inicial '{}'. Cambia ADMIN_PASSWORD antes de usar esto fuera de desarrollo.", adminUsername);
        }
    }

    private void sembrarRegimen(String nombre, int diasVacaciones, String factorLiquidacion, boolean asignacionFamiliar) {
        RegimenLaboral existente = regimenRepository.findAll().stream()
                .filter(r -> nombre.equals(r.getNombre())).findFirst().orElse(null);
        if (existente != null) {
            // Regímenes creados antes de existir la columna: se completa sin tocar lo demás
            if (existente.getAsignacionFamiliar() == null) {
                existente.setAsignacionFamiliar(asignacionFamiliar);
                regimenRepository.save(existente);
            }
            return;
        }
        RegimenLaboral r = new RegimenLaboral();
        r.setNombre(nombre);
        r.setDiasVacaciones(diasVacaciones);
        r.setFactorLiquidacion(new BigDecimal(factorLiquidacion));
        r.setAsignacionFamiliar(asignacionFamiliar);
        regimenRepository.save(r);
    }

    /** Tabla del comunicado de tardanzas (entrada 9:00). Los tramos son minutos de atraso. */
    private void sembrarReglasTardanza() {
        if (reglaTardanzaRepository.count() > 0) return;
        reglaTardanza(1, 1, 20, ReglaTardanza.MONTO, "7.00", "De 9:01 a 9:20");
        reglaTardanza(2, 21, 60, ReglaTardanza.MONTO, "15.00", "De 9:21 a 10:00");
        reglaTardanza(3, 61, 100, ReglaTardanza.MEDIO_DIA, null, "De 10:01 a 10:40: medio día");
    }

    private void reglaTardanza(int orden, int desde, Integer hasta, String tipo, String monto, String descripcion) {
        ReglaTardanza r = new ReglaTardanza();
        r.setOrden(orden);
        r.setDesdeMinuto(desde);
        r.setHastaMinuto(hasta);
        r.setTipo(tipo);
        r.setMonto(monto == null ? null : new BigDecimal(monto));
        r.setDescripcion(descripcion);
        reglaTardanzaRepository.save(r);
    }

    /** Feriados nacionales 2026 (16). Fuente: calendario publicado en prensa; confirmar y ajustar con /api/feriados. */
    private void sembrarFeriados2026() {
        if (!feriadoRepository.findByFechaBetweenOrderByFecha(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).isEmpty()) return;
        feriado(1, 1, "Año Nuevo");
        feriado(4, 2, "Jueves Santo");
        feriado(4, 3, "Viernes Santo");
        feriado(5, 1, "Día del Trabajo");
        feriado(6, 7, "Batalla de Arica y Día de la Bandera");
        feriado(6, 29, "San Pedro y San Pablo");
        feriado(7, 23, "Día de la Fuerza Aérea del Perú");
        feriado(7, 28, "Fiestas Patrias");
        feriado(7, 29, "Fiestas Patrias");
        feriado(8, 6, "Batalla de Junín");
        feriado(8, 30, "Santa Rosa de Lima");
        feriado(10, 8, "Combate de Angamos");
        feriado(11, 1, "Todos los Santos");
        feriado(12, 8, "Inmaculada Concepción");
        feriado(12, 9, "Batalla de Ayacucho");
        feriado(12, 25, "Navidad");
    }

    private void feriado(int mes, int dia, String nombre) {
        Feriado f = new Feriado();
        f.setFecha(LocalDate.of(2026, mes, dia));
        f.setNombre(nombre);
        feriadoRepository.save(f);
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
