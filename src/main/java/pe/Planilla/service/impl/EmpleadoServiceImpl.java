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
        // --- Identidad
        TipoDocumento tipo = TipoDocumento.desdeTexto(dto.getTipoDocumento());
        String numero = dto.getDni() == null ? null : dto.getDni().trim();
        String errorNumero = tipo.validarNumero(numero);
        if (errorNumero != null) {
            throw new RuntimeException(errorNumero);
        }
        boolean repetido = empleadoRepository.findAllByDni(numero).stream()
                .anyMatch(e -> TipoDocumento.desdeTexto(e.getTipoDocumento()) == tipo);
        if (repetido) {
            throw new RuntimeException("Ya existe un empleado con " + tipo.getEtiqueta() + " " + numero + ".");
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

        // --- Sistema de pensiones: ONP por defecto; AFP exige elegir el fondo
        String sistema = esVacio(dto.getSistemaPensionario()) ? "ONP" : dto.getSistemaPensionario().trim().toUpperCase();
        if (!sistema.equals("ONP") && !sistema.equals("AFP")) {
            throw new RuntimeException("El sistema pensionario debe ser ONP o AFP.");
        }
        Afp afp = null;
        if (sistema.equals("AFP")) {
            if (dto.getIdAfp() == null) {
                throw new RuntimeException("Seleccione la AFP del empleado.");
            }
            afp = afpRepository.findById(dto.getIdAfp())
                    .orElseThrow(() -> new RuntimeException("AFP no encontrada."));
        }

        Empleado empleado = new Empleado();
        empleado.setTipoDocumento(tipo.name());
        empleado.setDni(numero);
        empleado.setNombres(dto.getNombres().trim());
        empleado.setApellidos(dto.getApellidos().trim());
        empleado.setFechaNacimiento(dto.getFechaNacimiento());
        empleado.setFechaIngreso(dto.getFechaIngreso());
        empleado.setCargo(limpiar(dto.getCargo()));
        empleado.setArea(limpiar(dto.getArea()));
        empleado.setCorreo(limpiar(dto.getCorreo()));
        empleado.setTelefono(limpiar(dto.getTelefono()));
        empleado.setSueldoBase(dto.getSueldoBase());
        empleado.setBanco(limpiar(dto.getBanco()));
        empleado.setCuentaBancaria(limpiar(dto.getCuentaBancaria()));
        empleado.setBancoCts(limpiar(dto.getBancoCts()));
        empleado.setCuentaCts(limpiar(dto.getCuentaCts()));
        empleado.setRegimenLaboral(regimen);
        empleado.setSistemaPensionario(sistema);
        empleado.setAfp(afp);
        empleado.setCuspp(sistema.equals("AFP") ? limpiar(dto.getCuspp()) : null);
        empleado.setAsignacionFamiliar(Boolean.TRUE.equals(dto.getAsignacionFamiliar()));
        empleado.setEstado("ACTIVO");
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
        dto.setFechaNacimiento(e.getFechaNacimiento());
        dto.setFechaIngreso(e.getFechaIngreso());
        dto.setCargo(e.getCargo());
        dto.setArea(e.getArea());
        dto.setCorreo(e.getCorreo());
        dto.setTelefono(e.getTelefono());
        dto.setSueldoBase(e.getSueldoBase());
        dto.setBanco(e.getBanco());
        dto.setCuentaBancaria(e.getCuentaBancaria());
        dto.setBancoCts(e.getBancoCts());
        dto.setCuentaCts(e.getCuentaCts());
        dto.setRegimenLaboral(e.getRegimenLaboral().getNombre());
        dto.setEstado(e.getEstado());
        dto.setSistemaPensionario(e.getSistemaPensionario() != null ? e.getSistemaPensionario() : "ONP");
        dto.setAfp(e.getAfp() != null ? e.getAfp().getNombre() : null);
        dto.setCuspp(e.getCuspp());
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
