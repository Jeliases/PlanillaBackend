package pe.Planilla.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.Planilla.config.AuditoriaConfig.Revision;
import pe.Planilla.model.entity.*;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * Historial de cambios de cualquier registro (quién, cuándo, qué campo cambió y de qué valor a cuál).
 * GET /api/auditoria/{entidad}/{id}  con entidad = empleado | asistencia | movimiento | boleta | concepto | afp | regimen | horario | feriado | regla-tardanza | planilla | usuario
 * GET /api/auditoria/recientes?limite=100  últimas revisiones del sistema.
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private static final Map<String, Class<?>> ENTIDADES = Map.ofEntries(
            Map.entry("empleado", Empleado.class), Map.entry("asistencia", AsistenciaDiaria.class),
            Map.entry("movimiento", MovimientoPlanilla.class), Map.entry("boleta", BoletaPago.class),
            Map.entry("concepto", ConceptoRemunerativo.class), Map.entry("afp", Afp.class),
            Map.entry("regimen", RegimenLaboral.class), Map.entry("horario", HorarioProgramado.class),
            Map.entry("feriado", Feriado.class), Map.entry("regla-tardanza", ReglaTardanza.class),
            Map.entry("planilla", PlanillaCabecera.class), Map.entry("usuario", Usuario.class));

    private static final Set<String> OCULTOS = Set.of("password", "class", "detalle", "authorities", "accountNonExpired",
            "accountNonLocked", "credentialsNonExpired", "enabled");

    private final EntityManager entityManager;

    @GetMapping("/{entidad}/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> historial(@PathVariable String entidad, @PathVariable Integer id) {
        Class<?> clase = ENTIDADES.get(entidad);
        if (clase == null) {
            throw new RuntimeException("Entidad desconocida. Use: " + String.join(", ", new TreeSet<>(ENTIDADES.keySet())));
        }
        AuditReader reader = AuditReaderFactory.get(entityManager);
        @SuppressWarnings("unchecked")
        List<Object[]> filas = reader.createQuery()
                .forRevisionsOfEntity(clase, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<Map<String, Object>> salida = new ArrayList<>();
        Map<String, Object> anterior = null;
        for (Object[] fila : filas) {
            Object entidadVersion = fila[0];
            Revision rev = (Revision) fila[1];
            RevisionType tipo = (RevisionType) fila[2];
            Map<String, Object> actual = tipo == RevisionType.DEL ? Map.of() : valores(entidadVersion);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("revision", rev.getId());
            item.put("fecha", Instant.ofEpochMilli(rev.getMarcaTiempo()).atZone(ZoneId.systemDefault()).toLocalDateTime().toString());
            item.put("usuario", rev.getUsuario());
            item.put("accion", switch (tipo) { case ADD -> "CREADO"; case MOD -> "MODIFICADO"; case DEL -> "ELIMINADO"; });
            item.put("cambios", diferencias(anterior, actual, tipo));
            salida.add(item);
            anterior = actual;
        }
        return ResponseEntity.ok(salida);
    }

    @GetMapping("/recientes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> recientes(@RequestParam(defaultValue = "100") int limite) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Map.Entry<String, Class<?>> e : ENTIDADES.entrySet()) {
            @SuppressWarnings("unchecked")
            List<Object[]> filas = reader.createQuery()
                    .forRevisionsOfEntity(e.getValue(), false, true)
                    .addOrder(AuditEntity.revisionNumber().desc())
                    .setMaxResults(Math.min(limite, 500))
                    .getResultList();
            for (Object[] fila : filas) {
                Revision rev = (Revision) fila[1];
                RevisionType tipo = (RevisionType) fila[2];
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("revision", rev.getId());
                item.put("fecha", Instant.ofEpochMilli(rev.getMarcaTiempo()).atZone(ZoneId.systemDefault()).toLocalDateTime().toString());
                item.put("usuario", rev.getUsuario());
                item.put("entidad", e.getKey());
                item.put("id", idDe(fila[0]));
                item.put("accion", switch (tipo) { case ADD -> "CREADO"; case MOD -> "MODIFICADO"; case DEL -> "ELIMINADO"; });
                item.put("resumen", resumen(e.getKey(), fila[0], tipo));
                salida.add(item);
            }
        }
        salida.sort((a, b) -> Integer.compare((int) b.get("revision"), (int) a.get("revision")));
        return ResponseEntity.ok(salida.subList(0, Math.min(limite, salida.size())));
    }

    // ------------------------------------------------------------- ayudas

    /** Valores simples de la entidad (los objetos relacionados se muestran por su nombre o id). */
    private Map<String, Object> valores(Object entidad) {
        Map<String, Object> m = new TreeMap<>();
        try {
            for (PropertyDescriptor p : Introspector.getBeanInfo(entidad.getClass()).getPropertyDescriptors()) {
                Method get = p.getReadMethod();
                if (get == null || OCULTOS.contains(p.getName())) continue;
                Object v = get.invoke(entidad);
                m.put(p.getName(), simplificar(v));
            }
        } catch (Exception e) {
            m.put("error", "no se pudo leer: " + e.getMessage());
        }
        return m;
    }

    private Object simplificar(Object v) {
        if (v == null) return null;
        if (v instanceof Empleado e) return e.getNombres() + " " + e.getApellidos();
        if (v instanceof RegimenLaboral r) return r.getNombre();
        if (v instanceof Afp a) return a.getNombre();
        if (v instanceof ConceptoRemunerativo c) return c.getCodigo() + " " + c.getDescripcion();
        if (v instanceof PlanillaCabecera p) return p.getPeriodo();
        if (v instanceof Collection<?>) return null;
        if (v instanceof Number || v instanceof Boolean || v instanceof CharSequence) return v;
        return v.toString();
    }

    private List<Map<String, Object>> diferencias(Map<String, Object> antes, Map<String, Object> despues, RevisionType tipo) {
        List<Map<String, Object>> cambios = new ArrayList<>();
        Set<String> claves = new TreeSet<>();
        if (antes != null) claves.addAll(antes.keySet());
        claves.addAll(despues.keySet());
        for (String k : claves) {
            Object a = antes == null ? null : antes.get(k);
            Object d = despues.get(k);
            boolean iguales = Objects.equals(String.valueOf(a), String.valueOf(d));
            if (tipo == RevisionType.ADD ? d == null : iguales) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("campo", k);
            c.put("antes", a);
            c.put("despues", d);
            cambios.add(c);
        }
        return cambios;
    }

    private Object idDe(Object entidad) {
        try {
            for (PropertyDescriptor p : Introspector.getBeanInfo(entidad.getClass()).getPropertyDescriptors()) {
                if (p.getName().startsWith("id") && p.getReadMethod() != null && p.getReadMethod().getReturnType() == Integer.class) {
                    return p.getReadMethod().invoke(entidad);
                }
            }
        } catch (Exception ignorada) {
            // sin id legible
        }
        return null;
    }

    private String resumen(String entidad, Object e, RevisionType tipo) {
        if (tipo == RevisionType.DEL) return "registro eliminado";
        return switch (entidad) {
            case "empleado" -> ((Empleado) e).getNombres() + " " + ((Empleado) e).getApellidos();
            case "asistencia" -> ((AsistenciaDiaria) e).getFecha() + " " + ((AsistenciaDiaria) e).getEstado();
            case "movimiento" -> ((MovimientoPlanilla) e).getPeriodo() + " S/ " + ((MovimientoPlanilla) e).getMonto();
            case "boleta" -> "neto S/ " + ((BoletaPago) e).getTotalNeto();
            case "concepto" -> ((ConceptoRemunerativo) e).getCodigo() + " " + ((ConceptoRemunerativo) e).getDescripcion();
            case "afp" -> ((Afp) e).getNombre();
            case "regimen" -> ((RegimenLaboral) e).getNombre();
            case "horario" -> ((HorarioProgramado) e).getFecha().toString();
            case "feriado" -> ((Feriado) e).getFecha() + " " + ((Feriado) e).getNombre();
            case "regla-tardanza" -> String.valueOf(((ReglaTardanza) e).getDescripcion());
            case "planilla" -> ((PlanillaCabecera) e).getPeriodo() + " " + ((PlanillaCabecera) e).getEstado();
            case "usuario" -> ((Usuario) e).getUsername() + " (" + ((Usuario) e).getRol() + ")";
            default -> "";
        };
    }
}
