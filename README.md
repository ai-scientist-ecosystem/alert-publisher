# Alert Publisher Service

Alert Publisher is a microservice responsible for publishing disaster alerts through multiple channels (Cell Broadcast and Firebase Cloud Messaging). It consumes alerts from Kafka and distributes them to relevant communication channels.

## Features

- **Dual-Channel Publishing**: Broadcasts alerts via Cell Broadcast (telecom API) and Firebase Cloud Messaging
- **Kafka Integration**: Consumes alerts from `alerts.candidates` topic
- **Async Processing**: Non-blocking operations with CompletableFuture and Reactive programming
- **Retry Mechanism**: Automatic retry for failed publications (configurable max attempts)
- **Status Tracking**: Independent status tracking for each publishing channel
- **REST API**: Query published alerts, view statistics, trigger retries
- **Service Discovery**: Eureka client integration
- **Database**: PostgreSQL for alert history and analytics

## Tech Stack

- **Spring Boot**: 3.2.5
- **Spring Kafka**: Message consumption and production
- **Spring Data JPA**: Database access
- **PostgreSQL**: Alert history storage
- **Firebase Admin SDK**: 9.2.0 (Push notifications)
- **Spring WebFlux**: Reactive HTTP client (Cell Broadcast API)
- **Lombok**: 1.18.34 (Code generation)
- **Java**: 17

## Architecture

```
Kafka Topic: alerts.candidates
         ↓
   AlertConsumer
         ↓
AlertPublisherService ──────┬──→ CellBroadcastService (Telecom API)
                            │
                            └──→ FirebaseMessagingService (FCM)
         ↓
  PostgreSQL Database
         ↓
Kafka Topics: alerts.published / alerts.failed
```

## Configuration

### Environment Variables

```yaml
# Server
server.port: 8084

# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/alert_publisher_db
spring.datasource.username: postgres
spring.datasource.password: postgres123

# Kafka
spring.kafka.bootstrap-servers: localhost:9092
spring.kafka.consumer.group-id: alert-publisher-group

# Kafka Topics
kafka.topics.alerts-input: alerts.candidates
kafka.topics.alerts-success: alerts.published
kafka.topics.alerts-failed: alerts.failed

# Cell Broadcast
cell-broadcast.api-url: https://api.telecom.example/broadcast
cell-broadcast.api-key: your-api-key
cell-broadcast.timeout-seconds: 30
cell-broadcast.max-retries: 3

# Firebase
fcm.credentials-path: /path/to/firebase-credentials.json
fcm.timeout-seconds: 30
fcm.max-retries: 3

# Eureka
eureka.client.service-url.defaultZone: http://admin:admin123@localhost:8761/eureka/
```

### Database Setup

```sql
CREATE DATABASE alert_publisher_db;

-- Tables are auto-created by JPA (spring.jpa.hibernate.ddl-auto=update)
```

### Firebase Setup

1. Create Firebase project at https://console.firebase.google.com
2. Generate service account credentials JSON
3. Set path in `fcm.credentials-path` configuration
4. For MVP: Mock implementation is used (no real Firebase required)

### Cell Broadcast Setup

1. Obtain API credentials from telecom provider
2. Set API URL and key in configuration
3. For MVP: Mock implementation is used (no real telecom API required)

## REST API Endpoints

### Get All Published Alerts
```http
GET /api/v1/published-alerts
```

### Get Alert by ID
```http
GET /api/v1/published-alerts/{alertId}
```

### Get Alerts by Severity
```http
GET /api/v1/published-alerts/severity/{severity}
```
Example: `/api/v1/published-alerts/severity/CRITICAL`

### Get Alerts by Type
```http
GET /api/v1/published-alerts/type/{alertType}
```
Example: `/api/v1/published-alerts/type/EARTHQUAKE`

### Get Alerts by Date Range
```http
GET /api/v1/published-alerts/date-range?startDate=2025-01-01T00:00:00&endDate=2025-12-31T23:59:59
```

### Get Failed Alerts
```http
GET /api/v1/published-alerts/failed
```

### Retry Failed Alerts
```http
POST /api/v1/published-alerts/retry-failed?maxRetries=3
```

### Get Publishing Statistics
```http
GET /api/v1/published-alerts/statistics?hours=24
```

Response:
```json
{
  "totalPublished": 150,
  "cellBroadcastSuccess": 142,
  "fcmSuccess": 147,
  "cellBroadcastSuccessRate": 0.9467,
  "fcmSuccessRate": 0.98
}
```

## Running Locally

### Prerequisites

- Java 17
- Maven 3.8+
- PostgreSQL 14+
- Kafka 3.x
- Docker (optional)

### Steps

1. **Start Infrastructure**
   ```bash
   cd infra
   docker-compose up -d postgres kafka
   ```

2. **Create Database**
   ```bash
   psql -U postgres -c "CREATE DATABASE alert_publisher_db;"
   ```

3. **Build Service**
   ```bash
   cd alert-publisher
   mvn clean package
   ```

4. **Run Service**
   ```bash
   mvn spring-boot:run
   ```

   Or run JAR:
   ```bash
   java -jar target/alert-publisher-0.0.1-SNAPSHOT.jar
   ```

5. **Verify Service**
   ```bash
   # Health check
   curl http://localhost:8084/actuator/health
   
   # Get published alerts
   curl http://localhost:8084/api/v1/published-alerts
   ```

## Docker Build

```bash
# Build image
docker build -t alert-publisher:latest .

# Run container
docker run -d \
  -p 8084:8084 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/alert_publisher_db \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  --name alert-publisher \
  alert-publisher:latest
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Manual Testing

1. Publish test alert to Kafka:
   ```bash
   kafka-console-producer --broker-list localhost:9092 --topic alerts.candidates
   ```
   
   Paste JSON:
   ```json
   {
     "alertId": "test-001",
     "severity": "HIGH",
     "alertType": "EARTHQUAKE",
     "message": "Earthquake detected in California",
     "detectedAt": "2025-12-11T12:00:00",
     "latitude": 34.0522,
     "longitude": -118.2437,
     "radius": 50.0
   }
   ```

2. Check database:
   ```sql
   SELECT * FROM published_alerts WHERE alert_id = 'test-001';
   ```

3. Verify Kafka results:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 --topic alerts.published --from-beginning
   ```

## Monitoring

### Health Check
```bash
curl http://localhost:8084/actuator/health
```

### Metrics
```bash
curl http://localhost:8084/actuator/metrics
```

### Prometheus Metrics
```bash
curl http://localhost:8084/actuator/prometheus
```

## Publishing Success Rates (MVP)

- **Cell Broadcast**: 95% success rate (mock)
- **Firebase Cloud Messaging**: 98% success rate (mock)

Production implementations will use actual telecom and Firebase APIs.

## Troubleshooting

### Service won't start
- Verify PostgreSQL is running: `psql -U postgres -c "SELECT version();"`
- Verify Kafka is running: `nc -zv localhost 9092`
- Check Eureka server: `curl http://localhost:8761`

### Alerts not consumed
- Check Kafka topic exists: `kafka-topics --bootstrap-server localhost:9092 --list`
- Verify consumer group: `kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group alert-publisher-group`

### Database errors
- Verify connection: `psql -U postgres -d alert_publisher_db -c "SELECT 1;"`
- Check JPA logs: Set `logging.level.org.hibernate.SQL=DEBUG`

## Project Structure

```
alert-publisher/
├── src/main/java/com/aiscientist/alert_publisher/
│   ├── AlertPublisherApplication.java       # Main application
│   ├── config/
│   │   ├── AsyncConfig.java                 # Async configuration
│   │   ├── KafkaConsumerConfig.java         # Kafka consumer config
│   │   ├── KafkaProducerConfig.java         # Kafka producer config
│   │   └── WebClientConfig.java             # WebClient config
│   ├── consumer/
│   │   └── AlertConsumer.java               # Kafka message consumer
│   ├── controller/
│   │   └── PublishedAlertController.java    # REST API
│   ├── dto/
│   │   └── AlertMessage.java                # Alert DTO
│   ├── model/
│   │   └── PublishedAlert.java              # JPA entity
│   ├── repository/
│   │   └── PublishedAlertRepository.java    # Data access
│   └── service/
│       ├── AlertPublisherService.java       # Main orchestration
│       ├── CellBroadcastService.java        # Cell Broadcast integration
│       └── FirebaseMessagingService.java    # FCM integration
├── src/main/resources/
│   └── application.yml                       # Configuration
├── pom.xml                                   # Maven dependencies
└── Dockerfile                                # Docker build
```

## License

See LICENSE file in repository root.

## Contributing

See CONTRIBUTING.md in repository root.
