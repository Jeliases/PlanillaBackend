package pe.Planilla.service;

import pe.Planilla.model.dto.AuthRequestDTO;
import pe.Planilla.model.dto.AuthResponseDTO;

public interface AuthService {
    AuthResponseDTO registrar(AuthRequestDTO request);
    AuthResponseDTO login(AuthRequestDTO request);
}