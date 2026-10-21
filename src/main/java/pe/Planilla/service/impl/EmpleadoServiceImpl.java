package pe.Planilla.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.Planilla.model.TipoDocumento;
import pe.Planilla.model.dto.EmpleadoRequestDTO;
import pe.Planilla.model.dto.EmpleadoResponseDTO;
import pe.Planilla.model.entity.Afp;
import pe.Planilla.model.entity.Empleado;
import pe.Planilla.model.entity.RegimenLaboral;
import pe.Planilla.realtime.NotificadorTiempoReal;
import pe.Planilla.repository.AfpRepository;
import pe.Planilla.repository.EmpleadoRepository;
import pe.Planilla.repository.RegimenLaboralRepository;
import pe.Planilla.service.EmpleadoService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpleadoServiceImpl implements EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final AfpRepository afpRepository;
    private final NotificadorTiempoReal notificador;

    @Override
    public List<EmpleadoResponseDTO> listarTodos() {
        return empleadoRepository.findAll().stream()
                .map(this::aDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EmpleadoResponseDTO registrarEmpleado(EmpleadoRequestDTO requestDTO) {
        Empleado empleado = construirEmpleado(requestDTO);
        EmpleadoResponseDTO dto = aDTO(empleadoRepository.save(empleado));
        notificador.publicar("empleados", "CREADO", dto.getIdEmpleado(), dto); // tiempo real
        return dto;
    }

    @Override
    @Transactional
    public List<EmpleadoResponseDTO> guardarLote(List<Empleado> empleados) {
        List<EmpleadoResponseDTO> guardados = empleadoRepository.saveAll(empleados).stream().map(this::aDTO).toList();
        // Una notificación por empleado: se envían al confirmarse la transacción completa
        guardados.forEach(dto -> notificador.publicar("empleados", "CREADO", dto.getIdEmpleado(), dto));
        return guardados;
    }

    @Override
    public Empleado construirEmpleado(EmpleadoRequestDTO dto) {
        return construir(dto, new Empleado());
    }

    @Override
    @Transactional
    public EmpleadoResponseDTO actualizar(Integer id, EmpleadoRequestDTO dto) {
        Empleado existente = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        // Los campos que no vienen se conservan: así la pantalla puede mandar solo lo que cambió
        EmpleadoRequestDTO completo = fusionar(existente, dto);
        Empleado actualizado = construir(completo, existente);
        EmpleadoResponseDTO salida = aDTO(empleadoRepository.save(actualizado));
        notificador.publicar("empleados", "ACTUALIZADO", salida.getIdEmpleado(), salida);
        return salida;
    }

    @Override
    @Transactional
    public EmpleadoResponseDTO cesar(Integer id, LocalDate fechaCese, String motivo) {
        Empleado e = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        if (fechaCese == null) {
            throw new RuntimeException("Indique la fecha de cese (último día trabajado).");
        }
        if (e.getFechaIngreso() != null && fechaCese.isBefore(e.getFechaIngreso())) {
            throw new RuntimeException("La fecha de cese no puede ser anterior al ingreso (" + e.getFechaIngreso() + ").");
        }
        e.setEstado("CESADO");
        e.setFechaCese(fechaCese);
        e.setMotivoCese(limpiar(motivo));
        EmpleadoResponseDTO salida = aDTO(empleadoRepository.save(e));
        notificador.publicar("empleados", "ACTUALIZADO", salida.getIdEmpleado(), salida);
        return salida;
    }

    @Override
    @Transactional
    public EmpleadoResponseDTO reactivar(Integer id) {
        Empleado e = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        e.setEstado("ACTIVO");
        e.setFechaCese(null);
        e.setMotivoCese(null);
        EmpleadoResponseDTO salida = aDTO(empleadoRepository.save(e));
        notificador.publicar("empleados", "ACTUALIZADO", salida.getIdEmpleado(), salida);
        return salida;
    }

    /** Rellena en el DTO los campos vacíos con los valores que ya tiene el empleado. */
    private EmpleadoRequestDTO fusionar(Empleado e, EmpleadoRequestDTO dto) {
        EmpleadoRequestDTO f = new EmpleadoRequestDTO();
        f.setTipoDocumento(esVacio(dto.getTipoDocumento()) ? e.getTipoDocumento() : dto.getTipoDocumento());
        f.setDni(esVacio(dto.getDni()) ? e.getDni() : dto.getDni());
        f.setIdMarcador(dto.getIdMarcador() != null ? dto.getIdMarcador() : e.getIdMarcador());
        f.setNombres(esVacio(dto.getNombres()) ? e.getNombres() : dto.getNombres());
        f.setApellidos(esVacio(dto.getApellidos()) ? e.getApellidos() : dto.getApellidos());
        f.setFechaNacimiento(dto.getFechaNacimiento() != null ? dto.getFechaNacimiento() : e.getFechaNacimiento());
        f.setFechaIngreso(dto.getFechaIngreso() != null ? dto.getFechaIngreso() : e.getFechaIngreso());
        f.setCargo(dto.getCargo() != null ? dto.getCargo() : e.getCargo());
        f.setArea(dto.getArea() != null ? dto.getArea() : e.getArea());
        f.setCorreo(dto.getCorreo() != null ? dto.getCorreo() : e.getCorreo());
        f.setTelefono(dto.getTelefono() != null ? dto.getTelefono() : e.getTelefono());
        f.setSueldoBase(dto.getSueldoBase() != null ? dto.getSueldoBase() : e.getSueldoBase());
        f.setHorasJornada(dto.getHorasJornada() != null ? dto.getHorasJornada() : e.getHorasJornada());
        f.setBanco(dto.getBanco() != null ? dto.getBanco() : e.getBanco());
        f.setCuentaBancaria(dto.getCuentaBancaria() != null ? dto.getCuentaBancaria() : e.getCuentaBancaria());
        f.setBancoCts(dto.getBancoCts() != null ? dto.getBancoCts() : e.getBancoCts());
        f.setCuentaCts(dto.getCuentaCts() != null ? dto.getCuentaCts() : e.getCuentaCts());
        f.setSupervisor(dto.getSupervisor() != null ? dto.getSupervisor() : e.getSupervisor());
        f.setSede(dto.getSede() != null ? dto.getSede() : e.getSede());
        f.setCartera(dto.getCartera() != null ? dto.getCartera() : e.getCartera());
        f.setFechaFinContrato(dto.getFechaFinContrato() != null ? dto.getFechaFinContrato() : e.getFechaFinContrato());
        f.setIdRegimen(dto.getIdRegimen() != null ? dto.getIdRegimen() : (e.getRegimenLaboral() != null ? e.getRegimenLaboral().getIdRegimen() : null));
        f.setSistemaPensionario(esVacio(dto.getSistemaPensionario()) ? e.getSistemaPensionario() : dto.getSistemaPensionario());
        boolean cambiaSistema = !esVacio(dto.getSistemaPensionario()) && !dto.getSistemaPensionario().trim().equalsIgnoreCase(e.getSistemaPensionario());
        f.setIdAfp(dto.getIdAfp() != null ? dto.getIdAfp() : (cambiaSistema ? null : (e.getAfp() != null ? e.getAfp().getIdAfp() : null)));
        f.setCuspp(dto.getCuspp() != null ? dto.getCuspp() : e.getCuspp());
        f.setTipoComisionAfp(esVacio(dto.getTipoComisionAfp()) ? e.getTipoComisionAfp() : dto.getTipoComisionAfp());
        f.setAsignacionFamiliar(dto.getAsignacionFamiliar() != null ? dto.getAsignacionFamiliar() : e.getAsignacionFamiliar());
        return f;
    }

    /** Valida el DTO completo y vuelca los datos en {@code empleado} (nuevo o existente). */
    private Empleado construir(EmpleadoRequestDTO dto, Empleado empleado) {
        Integer idActual = empleado.getIdEmpleado(); // null si es nuevo

        // --- Identidad
        TipoDocumento tipo = TipoDocumento.desdeTexto(dto.getTipoDocumento());
        String numero = dto.getDni() == null ? null : dto.getDni().trim();
        String errorNumero = tipo.validarNumero(numero);
        if (errorNumero != null) {
            throw new RuntimeException(errorNumero);
        }
        // "Otro" empleado = uno con distinto id (al crear, idActual es null: cualquiera existente es otro)
        boolean repetido = empleadoRepository.findAllByDni(numero).stream()
                .filter(e -> idActual == null || !idActual.equals(e.getIdEmpleado()))
                .anyMatch(e -> TipoDocumento.desdeTexto(e.getTipoDocumento()) == tipo);
        if (repetido) {
            throw new RuntimeException("Ya existe un empleado con " + tipo.getEtiqueta() + " " + numero + ".");
        }
        String idMarcador = limpiar(dto.getIdMarcador());
        if (idMarcador != null && empleadoRepository.findFirstByIdMarcador(idMarcador)
                .filter(e -> idActual == null || !idActual.equals(e.getIdEmpleado())).isPresent()) {
            throw new RuntimeException("El ID del marcador " + idMarcador + " ya lo usa otro empleado.");
        }
        if (esVacio(dto.getNombres()) || esVacio(dto.getApellidos())) {
            throw new RuntimeException("Los nombres y apellidos son obligatorios.");
        }

        // --- Contrato
        if (dto.getFechaIngreso() == null) {
            throw new RuntimeException("La fecha de ingreso es obligatoria.");
        }
        if (dto.getFechaIngreso().isAfter(LocalDate.now().plusYears(1))) {
            throw new RuntimeException("La fecha de ingreso " + dto.getFechaIngreso() + " está demasiado lejos en el futuro.");
        }
        if (dto.getFechaNacimiento() != null && !dto.getFechaNacimiento().isBefore(dto.getFechaIngreso())) {
            throw new RuntimeException("La fecha de nacimiento debe ser anterior a la fecha de ingreso.");
        }
        if (dto.getSueldoBase() == null || dto.getSueldoBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El sueldo base debe ser mayor a cero.");
        }
        if (dto.getIdRegimen() == null) {
            throw new RuntimeException("El régimen laboral es obligatorio.");
        }
        RegimenLaboral regimen = regimenLaboralRepository.findById(dto.getIdRegimen())
                .orElseThrow(() -> new RuntimeException("Régimen Laboral no encontrado."));

        // --- Contacto
        if (!esVacio(dto.getCorreo()) && !dto.getCorreo().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new RuntimeException("El correo «" + dto.getCorreo().trim() + "» no es válido.");
        }

        // --- Jornada: 8 horas (completa) por defecto; con menos horas el básico se paga proporcional
        Integer horasJornada = dto.getHorasJornada() == null ? 8 : dto.getHorasJornada();
        if (horasJornada < 1 || horasJornada > 8) {
            throw new RuntimeException("Las horas de jornada deben estar entre 1 y 8 (8 = tiempo completo).");
        }

        // --- Sistema de pensiones: ONP por defecto; AFP exige elegir el fondo; NINGUNO = jubilado / no aportante
        String sistema = esVacio(dto.getSistemaPensionario()) ? "ONP" : dto.getSistemaPensionario().trim().toUpperCase();
        if (sistema.startsWith("SIN") || sistema.startsWith("NO ") || sistema.startsWith("NING")) sistema = "NINGUNO";
        if (!sistema.equals("ONP") && !sistema.equals("AFP") && !sistema.equals("NINGUNO")) {
            throw new RuntimeException("El sistema pensionario debe ser ONP, AFP o NINGUNO (no aportante).");
        }
        Afp afp = null;
        if (sistema.equals("AFP")) {
            if (dto.getIdAfp() == null) {
                throw new RuntimeException("Seleccione la AFP del empleado.");
            }
            afp = afpRepository.findById(dto.getIdAfp())
                    .orElseThrow(() -> new RuntimeException("AFP no encontrada."));
        }
        // Comisión AFP: FLUJO por defecto; MIXTA no paga comisión por flujo (solo 10% + prima)
        String tipoComision = null;
        if (sistema.equals("AFP")) {
            tipoComision = esVacio(dto.getTipoComisionAfp()) ? "FLUJO" : dto.getTipoComisionAfp().trim().toUpperCase();
            if (!tipoComision.equals("FLUJO") && !tipoComision.equals("MIXTA")) {
                throw new RuntimeException("El tipo de comisión AFP debe ser FLUJO o MIXTA.");
            }
        }

        empleado.setTipoDocumento(tipo.name());
        empleado.setDni(numero);
        empleado.setIdMarcador(idMarcador);
        empleado.setNombres(dto.getNombres().trim());
        empleado.setApellidos(dto.getApellidos().trim());
        empleado.setFechaNacimiento(dto.getFechaNacimiento());
        empleado.setFechaIngreso(dto.getFechaIngreso());
        empleado.setCargo(limpiar(dto.getCargo()));
        empleado.setArea(limpiar(dto.getArea()));
        empleado.setCorreo(limpiar(dto.getCorreo()));
        empleado.setTelefono(limpiar(dto.getTelefono()));
        empleado.setSueldoBase(dto.getSueldoBase());
        empleado.setHorasJornada(horasJornada);
        empleado.setBanco(limpiar(dto.getBanco()));
        empleado.setCuentaBancaria(limpiar(dto.getCuentaBancaria()));
        empleado.setBancoCts(limpiar(dto.getBancoCts()));
        empleado.setCuentaCts(limpiar(dto.getCuentaCts()));
        empleado.setSupervisor(limpiar(dto.getSupervisor()));
        empleado.setSede(limpiar(dto.getSede()));
        empleado.setCartera(limpiar(dto.getCartera()));
        empleado.setFechaFinContrato(dto.getFechaFinContrato());
        empleado.setRegimenLaboral(regimen);
        empleado.setSistemaPensionario(sistema);
        empleado.setAfp(afp);
        empleado.setCuspp(sistema.equals("AFP") ? limpiar(dto.getCuspp()) : null);
        empleado.setTipoComisionAfp(tipoComision);
        empleado.setAsignacionFamiliar(Boolean.TRUE.equals(dto.getAsignacionFamiliar()));
        if (empleado.getEstado() == null) empleado.setEstado("ACTIVO"); // un cesado editado sigue cesado
        return empleado;
    }

    @Override
    public EmpleadoResponseDTO buscarPorId(Integer id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado."));
        return aDTO(empleado);
    }

    @Override
    public EmpleadoResponseDTO aDTO(Empleado e) {
        EmpleadoResponseDTO dto = new EmpleadoResponseDTO();
        dto.setIdEmpleado(e.getIdEmpleado());
        dto.setTipoDocumento(e.getTipoDocumento() != null ? e.getTipoDocumento() : TipoDocumento.DNI.name());
        dto.setNombresCompletos(e.getNombres() + " " + e.getApellidos());
        dto.setDni(e.getDni());
        dto.setIdMarcador(e.getIdMarcador());
        dto.setFechaNacimiento(e.getFechaNacimiento());
        dto.setFechaIngreso(e.getFechaIngreso());
        dto.setCargo(e.getCargo());
        dto.setArea(e.getArea());
        dto.setCorreo(e.getCorreo());
        dto.setTelefono(e.getTelefono());
        dto.setSueldoBase(e.getSueldoBase());
        dto.setHorasJornada(e.getHorasJornada() != null ? e.getHorasJornada() : 8);
        dto.setBanco(e.getBanco());
        dto.setCuentaBancaria(e.getCuentaBancaria());
        dto.setBancoCts(e.getBancoCts());
        dto.setCuentaCts(e.getCuentaCts());
        dto.setSupervisor(e.getSupervisor());
        dto.setSede(e.getSede());
        dto.setCartera(e.getCartera());
        dto.setFechaFinContrato(e.getFechaFinContrato());
        dto.setRegimenLaboral(e.getRegimenLaboral().getNombre());
        dto.setEstado(e.getEstado());
        dto.setSistemaPensionario(e.getSistemaPensionario() != null ? e.getSistemaPensionario() : "ONP");
        dto.setAfp(e.getAfp() != null ? e.getAfp().getNombre() : null);
        dto.setCuspp(e.getCuspp());
        dto.setTipoComisionAfp(e.getTipoComisionAfp());
        dto.setFechaCese(e.getFechaCese());
        dto.setMotivoCese(e.getMotivoCese());
        dto.setAsignacionFamiliar(Boolean.TRUE.equals(e.getAsignacionFamiliar()));
        return dto;
    }

    private boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    private String limpiar(String s) {
        return esVacio(s) ? null : s.trim();
    }
}
