# ms-mensajeria

Microservicio de mensajería de **Tienda Native** (DSY1107 · Desarrollo Cloud Native I).
Consume los eventos que publica `ms-productos`, maneja errores con ACK manual y DLQ,
y expone un administrador REST de RabbitMQ.

## Topología

| Exchange (tipo) | Routing key | Cola | DLQ |
|---|---|---|---|
| `productos.direct.exchange` (direct) | `producto.creado`, `producto.actualizado`, `producto.eliminado`, `stock.bajo` | `auditoria.queue` | `auditoria.dlq` |
| `productos.direct.exchange` (direct) | `stock.bajo` | `inventario.queue` | `inventario.dlq` |
| `notificaciones.topic.exchange` (topic) | `notificacion.#` | `notificaciones.queue` | `notificaciones.dlq` |

- Todas las colas son **quorum** (replicadas en el clúster) y durables.
- Los mensajes muertos pasan por `mensajeria.dlx` (direct) a la DLQ de su cola.
- Las DLQ retienen los mensajes 24 h.
- Todos los nombres se definen en un solo lugar: el bloque `mensajeria` de [`application.yml`](src/main/resources/application.yml).

## Consumidores y ACK

Hay un consumidor por dominio: `auditoria`, `inventario` (también **produce** notificaciones en el topic), `notificaciones` y `dlq`.

Todos usan ACK manual a través de [`MensajeAck`](src/main/java/com/tiendanative/mensajeria/common/MensajeAck.java):

| Resultado | Decisión |
|---|---|
| Procesado | `basicAck` |
| `ErrorTransitorio` | `basicNack(requeue=true)` con backoff, hasta `max-intentos` (3) |
| Intentos agotados, `ErrorPermanente` o JSON inválido | `basicNack(requeue=false)` → DLQ, registrado en el log |

Para la demo: un producto con **FALLA** en el nombre simula un error transitorio, y uno con **INVALIDO**, un error permanente.

## API del administrador

Requiere un JWT de Cognito. Los `GET` aceptan cualquier sesión; los `POST` y `DELETE` exigen el scope `productos/write`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/admin/queues` | Estado de las colas del sistema |
| GET | `/api/v1/admin/queues/{nombre}` | Mensajes y consumidores |
| POST | `/api/v1/admin/queues` | `{nombre, tipo: CLASSIC\|QUORUM, durable, conDlq}` |
| DELETE | `/api/v1/admin/queues/{nombre}` | Elimina una cola |
| POST | `/api/v1/admin/queues/{nombre}/purge` | Vacía una cola |
| POST | `/api/v1/admin/exchanges` | `{nombre, tipo: DIRECT\|TOPIC\|FANOUT, durable}` |
| DELETE | `/api/v1/admin/exchanges/{nombre}` | Elimina un exchange |
| POST / DELETE | `/api/v1/admin/bindings` | `{cola, exchange, routingKey}` |
| GET | `/api/v1/admin/listeners` | Consumidores y su estado |
| POST | `/api/v1/admin/listeners/{id}/start` · `/stop` | Inicia o detiene un consumidor |
| GET | `/api/v1/eventos` | Últimos eventos procesados (panel del front) |

Códigos de respuesta:

| Código | Significado |
|---|---|
| 400 | Validación (nombres vacíos, prefijo `amq.`, quorum no durable, tipo desconocido) |
| 404 | El recurso no existe |
| 409 | Ya existe, o es parte de la topología del sistema |

## Ejecutar en local

1. Levanta el clúster de RabbitMQ (3 nodos):
   ```bash
   cd rabbitmq-cluster && docker compose up -d
   ```
   Dashboard: http://localhost:15672 (guest/guest).
2. Arranca el microservicio:
   ```bash
   mvn spring-boot:run
   ```
   Corre en el puerto **8081**.
3. Ejecuta las pruebas:
   ```bash
   mvn test
   ```

Variables de entorno:

| Variable | Por defecto |
|---|---|
| `RABBITMQ_ADDRESSES` | `localhost:5672,localhost:5673,localhost:5674` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` |
| `COGNITO_USER_POOL_ID` | `us-east-1_SOtYxhxql` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` y la URL de Amplify |
| `PORT` | `8081` |
