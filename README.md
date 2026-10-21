# Sistema de Planillas (Perú) — backend

Spring Boot 3 + MySQL. La pantalla (Angular) está en `C:\PlanillaFrontEd` y, para el deploy, se incluye dentro de este jar.

## Levantar en desarrollo

1. Base de datos propia (MySQL en Docker, puerto 3307):

```bash
docker compose up -d
```

2. Backend: desde IntelliJ (Run `PlanillaApplication`) o con el jar:

```bash
mvn -DskipTests package
```

```bash
java -jar target/Planilla-0.0.1-SNAPSHOT.jar
```

   Puerto por defecto `6903` (`server.port` en `application.properties`, o variable `PORT`).

3. Pantalla: en `C:\PlanillaFrontEd`, `npm start` → `http://localhost:4200`. La dirección del backend está en `src/environments/environment.ts`.

Usuario inicial `admin` con la contraseña de `ADMIN_PASSWORD` (por defecto `admin123`). Al primer ingreso el sistema obliga a cambiarla.

## Variables de entorno

| Variable | Para qué | Por defecto |
| --- | --- | --- |
| `PORT` | Puerto del servidor | 6903 |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión MySQL | localhost:3307 / root / mysql |
| `JWT_SECRET` | Clave de los tokens (Base64, 256 bits o más). **Cambiarla fuera de desarrollo.** | clave de desarrollo |
| `ADMIN_USER`, `ADMIN_PASSWORD` | Primer usuario (solo se crea si no hay usuarios) | admin / admin123 |
| `CORS_ORIGINS` | Orígenes permitidos (coma). Admite comodines | `http://localhost:*` |
| `RMV`, `UIT` | Parámetros legales del año | 1230 / 5500 |
| `EMPRESA_RUC`, `EMPRESA_RAZON_SOCIAL`, `EMPRESA_DIRECCION` | Datos del empleador (boleta y PLAME) | Informa Perú |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_HABILITADO` | Envío de boletas por correo (SMTP). Sin `MAIL_HABILITADO=true` queda apagado | apagado |

Tasas de AFP, tabla de tardanzas, feriados y regímenes viven en la base (se siembran al arrancar y se editan por API).

## Roles

- **ADMIN** (contador): todo.
- **GERENCIA**: solo lectura (todos los GET). No ve usuarios ni auditoría.

## Flujo mensual

1. Empleados → Carga masiva (Excel) la primera vez; después «Actualizar en masa» para sueldos.
2. Asistencias → Cargar horarios (tareo) si rotan de turno → Importar del marcador (Excel del reloj).
3. Revisar tardanzas/faltas: justificar las que tengan documento; autorizar horas extras.
4. Movimientos: bonos, descuentos, vacaciones.
5. Planilla → Procesar → revisar boletas (PDF) → Cerrar.
6. Reportes: resumen Excel, boletas PDF, archivo de banco, AFPnet, PLAME (.rem), envío por correo.

## Despliegue

### Producción (servidor Sulotec en Oracle Cloud)

El kit está en `Sulotec/Sulotec-infra`, carpeta `apps/planilla/` (README con el paso a paso). Resumen:

- Un solo contenedor `planilla`: el `Dockerfile` del kit compila la pantalla Angular, la copia a
  `src/main/resources/static/` y construye el jar; Spring Boot sirve pantalla, API y WebSocket en la misma dirección.
- Base MySQL `planilla` en la MySQL compartida de la VM; configuración y secretos solo en `/opt/sulotec/planilla.env`
  (`preparar.sh` lo crea y genera la clave inicial de `admin`, que se muestra una sola vez).
- Dirección `planilla.sulotec.com` por el túnel de Cloudflare, con Cloudflare Access delante.
- `/actuator/health` es el único endpoint de Actuator expuesto (sin detalles) y lo usa el chequeo de salud de Docker.
- Si `ADMIN_PASSWORD` está vacía y no hay usuarios, el seed **no** crea el usuario inicial (lo avisa en el log).

### Demo local (enlace temporal)

`C:\PlanillaDemo\iniciar-demo.bat` levanta el jar con la pantalla incluida en el puerto 8085 y su propia base
(`bd_planilla_demo`). Para un enlace público temporal:

```bash
& 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel --url http://localhost:8085
```

Para regenerar el jar del demo con la pantalla: `ng build` en el frontend, copiar `dist/planilla-frontend/browser/*`
a `src/main/resources/static/`, `mvn -DskipTests package` y copiar el jar a `C:\PlanillaDemo\planilla-demo.jar`.
El `Dockerfile` de este repo construye solo el backend (la pantalla hay que copiarla a `static/` antes); para
producción usa el del kit.

## Pruebas

```bash
mvn test
```

49 pruebas de cálculo (planilla, asistencia, liquidación) e importación de Excel.

## Pendiente de confirmar con la contadora

- Tardanzas de más de 10:40 (hoy: medio día) y si cada 5 tardanzas descuenta otro día o solo una vez.
- Gratificación: SUNAT la considera afecta a Renta de 5ta; la empresa dijo que no retiene.
- Formato exacto del archivo del banco (Interbank/BBVA) y de AFPnet: hoy son provisionales.
- Códigos PLAME 0406/0312 (gratificación) vigentes.
- Redondeo: hoy a dos decimales (0.005 sube).
