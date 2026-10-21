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

56 pruebas de cálculo (planilla, asistencia, liquidación, casos de las hojas de la contadora) e importación de Excel.

## Reglas tomadas de las hojas de la contadora (2026-10-21)

Se revisaron fórmula por fórmula sus Excel de CTS (mayo 2026), gratificación (julio 2026), planilla mensual (setiembre 2026)
y cálculo de quinta 2026. Las tasas de AFP/ONP, los meses de gratificación/CTS y la renta de 5ta de un sueldo constante
coinciden (hay pruebas con sus mismos números en `ReglasContadoraTest`). Lo que faltaba y ya está:

- **Jornada parcial**: `horasJornada` por empleado (8 = completa). El básico se paga sueldo / 8 x horas; con menos de 4 h no hay CTS.
- **Pensión "NINGUNO"** (jubilado / no aportante): sin descuento de pensión.
- **Tardanzas** reducen la remuneración afecta: pensión, EsSalud y renta de 5ta se calculan después de restarlas.
- **Ingresos no remunerativos** (movilidad 0904, capacitación 0915, bono por condición de trabajo): se pagan pero no entran a
  pensión/EsSalud (`afectaEssalud = false`) ni a 5ta (`afectaRenta5ta = false`). Los conceptos se siembran al arrancar.
- **Descuentos varios** como movimientos: adelanto 0701, préstamo 0702, menú, penalidad, equipos, EPS del trabajador, otros.
- **Feriado laborado** (0107): (básico + asignación familiar) / 30 por día (`POST /api/movimientos/feriado`).
- **Promedio de variables** (horas extras y comisiones del semestre / 6) en la base de la gratificación y de la CTS.
- **Adelanto de quincena**: `GET/POST /api/planillas/{periodo}/quincena[/registrar]` y Excel para el banco; queda como descuento 0701.
- **Constancias en PDF**: liquidación de gratificaciones y de depósito de CTS por trabajador
  (`/api/reportes/planillas/{periodo}/constancias/{gratificacion|cts}.pdf`). Quien firma sale de `EMPRESA_REPRESENTANTE` y
  `EMPRESA_REPRESENTANTE_DOC` (variables de entorno; vacías = línea para firmar).

## Pendiente de confirmar con la contadora

- **Faltas en la gratificación**: su Excel resta el promedio en soles de (horas extras − tardanzas − faltas) / 6 ÷ 2; el sistema
  descuenta 1/180 por falta (ley). Y si las tardanzas deben restar de la base de CTS (su Excel sí, la ley no).
- **Quiénes van a la mitad (/2)** en gratificación y CTS: ¿pequeña empresa (REMYPE) o jornada parcial? El sistema lo maneja
  con el régimen del empleado (factor 0.5).
- **Horas extras**: su Excel paga 25 % plano y el valor hora incluye la asignación familiar; el sistema 25 % las 2 primeras
  horas y 35 % después, sobre el básico.
- **Asignación familiar con faltas o permisos**: su Excel la paga completa; el sistema la prorratea por días.
- **Renta de 5ta**: su Excel proyecta sueldo x 12 + 2 gratificaciones con bonificación y divide el impuesto en 12 cuotas fijas;
  el sistema sigue el procedimiento mensual de SUNAT (igual total anual con sueldo constante; los tramos altos de su Excel
  todavía usan UIT 4 600).
- **Regla de los 3 meses** para promediar variables (la ley exige haberlas percibido 3 meses del semestre; su Excel divide
  entre 6 siempre, igual que ahora el sistema).
- EsSalud: el sistema aplica el piso de la RMV; su Excel no. RxH sin retención del 8 % (igual en ambos): confirmar.
- Tardanzas de más de 10:40 (hoy: medio día) y si cada 5 tardanzas descuenta otro día o solo una vez.
- Formato exacto del archivo del banco (Interbank/BBVA) y de AFPnet: hoy son provisionales.
- Códigos PLAME 0406/0312 (gratificación), 0102 (comisiones), 0107 (feriado), 0904/0915 (condición de trabajo).
- Redondeo: hoy a dos decimales (0.005 sube).
- La "planilla extra" (sueldo real distinto al declarado, pagos "x fuera") no se implementa: el sistema solo maneja lo declarado.
