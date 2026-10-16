package pe.Planilla.realtime;

import java.time.Instant;

/**
 * Mensaje que viaja por WebSocket a todos los clientes conectados.
 *
 * @param entidad  tema al que pertenece: empleados, asistencias, movimientos, conceptos, boletas
 * @param accion   CREADO, ACTUALIZADO, ELIMINADO, PROCESADO...
 * @param id       identificador del registro afectado (puede ser null)
 * @param data     el registro ya convertido a DTO, para que el cliente lo pinte sin volver a pedirlo
 * @param momento  instante en que se confirmó el cambio en la base de datos
 */
public record EventoTiempoReal(String entidad, String accion, Object id, Object data, Instant momento) {
}
