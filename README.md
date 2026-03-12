# Worker Consumer - Messaging Platform

Microservicio que consume mensajes de una cola RabbitMQ, aplica reglas de negocio (rate limiting por destinatario) y los persiste en MongoDB. Expone una API de consulta para buscar mensajes por linea de destino.

## Tecnologias

- Java 21
- Spring Boot 3.4.3
- MongoDB 7.0
- RabbitMQ
- Docker

## Prerequisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado y en ejecucion

No necesitas instalar Java, Maven, MongoDB ni ningun otro software. Todo corre dentro de containers Docker.

## Inicio rapido

### 1. Levantar RabbitMQ (servicio compartido)

RabbitMQ es la infraestructura de mensajeria compartida entre ambos microservicios. Si ya lo levantaste con el api-producer, omite este paso.

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

### 2. Levantar el Worker Consumer

```bash
cd worker-consumer
docker compose up --build -d
```

Esto levanta:
- **MongoDB 7.0** como base de datos de mensajes procesados
- **Mongo Express** (UI web para consultar MongoDB)
- **Worker Consumer** (la aplicacion Spring Boot)

> La primera ejecucion tarda unos minutos mientras descarga imagenes y dependencias Maven. Las siguientes son casi instantaneas.

### 3. Verificar que todo esta corriendo

```bash
docker ps
```

Deberias ver 3 containers activos: `worker-consumer-app`, `worker-consumer-mongodb`, `worker-consumer-mongo-express`.

## Servicios disponibles

| Servicio | URL | Descripcion |
|----------|-----|-------------|
| Worker Consumer API | http://localhost:8081 | API de consulta de mensajes |
| Mongo Express | http://localhost:8083 | UI web para consultar MongoDB |
| RabbitMQ Management | http://localhost:15672 | UI de gestion de colas |

### Credenciales

| Servicio | Usuario | Password |
|----------|---------|----------|
| Mongo Express | `admin` | `pass` (default) |
| RabbitMQ | `guest` | `guest` |

## Endpoints

### Consultar mensajes por destino

```bash
curl http://localhost:8081/messages/+573101112222
```

**Respuesta (200 OK):**
```json
[
  {
    "id": "665a1b2c3d4e5f6g7h8i9j0k",
    "origin": "+573001234567",
    "destination": "+573101112222",
    "messageType": "TEXT",
    "content": "Hola, este es un mensaje de prueba",
    "processingTime": 45,
    "createdDate": "2025-03-11T10:30:00",
    "error": null
  }
]
```

### Mensaje con rate limit excedido

Cuando un destinatario recibe mas de 3 mensajes en 24 horas, el mensaje se persiste con un error:

```json
{
  "id": "665a1b2c3d4e5f6g7h8i9j1l",
  "origin": "+573001234567",
  "destination": "+573101112222",
  "messageType": "TEXT",
  "content": "Cuarto mensaje",
  "processingTime": 32,
  "createdDate": "2025-03-11T12:00:00",
  "error": "Rate limit exceeded: destination '+573101112222' has received 3 messages in the last 24 hours (max: 3)"
}
```

### Health check

```bash
curl http://localhost:8081/actuator/health
```

## Reglas de negocio

- **Rate limiting:** Un mismo destinatario no puede recibir mas de **3 mensajes en un rango de 24 horas**.
- Si se excede el limite, el mensaje se persiste en MongoDB con el campo `error` indicando el motivo.
- Los mensajes con error **no cuentan** para el limite (solo los exitosos).

## Mecanismo de reintentos

- Si ocurre un error tecnico al procesar un mensaje, se reintenta hasta **3 veces** con un delay de **5 segundos** entre cada intento.
- Si se agotan los reintentos, el mensaje se envia a la **Dead Letter Queue (DLQ)**.

## Apagar los servicios

```bash
docker compose down

# Para eliminar los datos persistidos:
docker compose down -v
```

## Estructura del proyecto

```
worker-consumer/
├── src/main/java/com/aldeamo/messaging/workerconsumer/
│   ├── config/          # RabbitMQ y Security
│   ├── consumer/        # RabbitMQ message listener
│   ├── controller/      # REST endpoint de consulta
│   ├── document/        # MongoDB document model
│   ├── exception/       # Manejo centralizado de errores
│   ├── repository/      # Spring Data MongoDB
│   └── service/         # Logica de negocio y rate limiting
├── Dockerfile           # Multi-stage build (JDK -> JRE Alpine)
├── compose.yaml         # Docker Compose (MongoDB + Mongo Express + App)
└── pom.xml
```

## Esquema MongoDB

Coleccion: `messages`

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | String | Identificador unico (generado por MongoDB) |
| origin | String | Linea de origen |
| destination | String | Linea de destino (indexado) |
| messageType | String | Tipo de mensaje (TEXT, IMAGE, VIDEO, DOCUMENT) |
| content | String | Contenido del mensaje o URL |
| processingTime | Long | Tiempo de procesamiento en milisegundos |
| createdDate | LocalDateTime | Fecha de creacion |
| error | String | Error de logica de negocio (null si exitoso) |

## Observabilidad

```bash
# Health check
curl http://localhost:8081/actuator/health

# Metricas Prometheus
curl http://localhost:8081/actuator/prometheus
```

Metricas personalizadas:
- `messages.processed` - Total de mensajes procesados exitosamente
- `messages.rate_limited` - Total de mensajes rechazados por rate limit
- `messages.processing.time` - Tiempo de procesamiento por mensaje

## Colas RabbitMQ

| Cola | Descripcion |
|------|-------------|
| `messaging.queue` | Cola principal de mensajes |
| `messaging.retry.queue` | Cola de reintentos (TTL: 5s) |
| `messaging.dlq.queue` | Dead Letter Queue (mensajes fallidos) |
