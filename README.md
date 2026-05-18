# spring-boot-elk-logging

> Centralized logging for Spring Boot with the **ELK stack** (Elasticsearch, Logstash, Kibana), structured JSON logs, distributed trace **and correlation** IDs propagated via MDC, and **ECS-compliant** field naming.


This is the companion project to the Medium article **["Centralized Logging: Spring Boot Meets Elasticsearch, Logstash, and Kibana"](#)**. The repo and the article are designed to be read together — the article explains *why* each piece looks the way it does; the repo is the working code you can run end-to-end.

---

## What it demonstrates

- **Structured JSON logging** via `logstash-logback-encoder`
- **Trace & correlation ID propagation** through SLF4J **MDC** — every log line in a request automatically carries the same `traceId` and `correlationId` (reused from the `X-Trace-Id` / `X-Correlation-Id` request headers if present, otherwise generated, and echoed back on the response), no plumbing needed in service code
- **Inbound HTTP exchange logging** (method, URL, query, status, duration, client IP, request/response headers, and optional bodies for loggable content-types)
- **ECS field naming in two layers** — the Logback encoder renames its built-in fields (`logger` → `log.logger`, `level` → `log.level`, stack traces → `error.stack_trace`, …) via `<fieldNames>`; Logstash then renames the remaining flat app fields to nested **ECS** paths (e.g. `http.method` → `http.request.method`, `correlationId` → `labels.correlation_id`)
- **Elasticsearch index template** with sensible default mappings (keyword fields, `ip` type, `long` duration)
- **Kibana Discover** + KQL queries for fast log exploration

---

## Architecture

```
Spring Boot App ──► ./logs/*.log ──► Logstash ──► Elasticsearch ──► Kibana
                                       │
                                       └─ rename → ECS, convert types, drop noise
```

| Component | Responsibility |
|---|---|
| Spring Boot | Generates structured JSON logs with trace & correlation IDs via Logback + `logstash-logback-encoder` (encoder also renames built-in fields to ECS via `<fieldNames>`) |
| Logstash | Tails log files, lifts the JSON, renames the remaining flat fields to ECS, ships to Elasticsearch |
| Elasticsearch | Indexes and stores logs for fast querying |
| Kibana | Visualizes and queries logs via KQL |

---

## Prerequisites

- Java 21+
- Maven 3.6+ (or use the included `./mvnw` wrapper)
- Docker Desktop / Docker Engine + Docker Compose
- **At least 4 GB of free RAM** for the ELK containers (Elasticsearch is hungry)

---

## Quick start

### 1. Clone & build

```bash
git clone https://github.com/<your-github-username>/spring-boot-elk-logging.git
cd spring-boot-elk-logging
./mvnw clean package -DskipTests
```

> **Note for first-time setup:** if `./mvnw` is missing (this repo ships only the
> `.mvn/wrapper/maven-wrapper.properties` file), generate the full wrapper once with
> `mvn -N wrapper:wrapper -Dmaven=3.9.9` — requires a local Maven install for that
> single command, after which `./mvnw` works on any machine.

### 2. Start the ELK stack

```bash
docker compose up -d
```

Wait ~60 seconds for Elasticsearch to be ready:

```bash
curl -s http://localhost:9200/_cluster/health?pretty
# {"status":"yellow", ...}
```

### 3. Apply the Elasticsearch index template

```bash
curl -X PUT "http://localhost:9200/_index_template/spring-logs" \
     -H "Content-Type: application/json" \
     -d @docs/index-template.json
```

### 4. Start the Spring Boot app

In a separate terminal (so the ELK containers stay running):

```bash
./mvnw spring-boot:run
```

The app writes structured JSON logs to `./logs/spring-boot-elk-logging.log`, which Logstash picks up automatically.

### 5. Generate traffic

```bash
# Successful queries
curl http://localhost:8080/authors
curl http://localhost:8080/books
curl http://localhost:8080/books/1

# A 404
curl -i http://localhost:8080/books/999

# A 400 (no author specified) — triggers an ERROR log in BookService
curl -i -X POST http://localhost:8080/books \
     -H "Content-Type: application/json" \
     -d '{"title": "Test Book"}'

# Pass trace / correlation IDs through (notice how they appear in every log line,
# and come back on the response as X-Trace-Id / X-Correlation-Id headers)
curl -i -H "X-Trace-Id: my-test-trace-001" \
        -H "X-Correlation-Id: my-test-corr-001" \
        http://localhost:8080/books
```

### 6. Explore in Kibana

1. Open <http://localhost:5601>
2. Go to **Stack Management → Data Views → Create data view**
3. Name: `spring-logs-*`, Time field: `@timestamp`
4. Open **Discover** in the left sidebar and select the new data view
5. Try these KQL queries:

```kql
service.name : "spring-boot-elk-logging"
```

```kql
log.level : "ERROR"
```

```kql
http.response.status_code >= 400
```

```kql
trace.id : "my-test-trace-001"
```

```kql
event.duration > 100000000        // requests slower than 100ms (duration in nanoseconds)
```

---

## Project layout

```
spring-boot-elk-logging/
├── docker/
│   └── logstash/
│       ├── config/logstash.yml             # Logstash global config
│       └── pipeline/logstash.conf          # input → filter → output pipeline
├── docs/
│   └── index-template.json                 # Elasticsearch mapping template
├── logs/                                   # Spring app writes JSON logs here
├── src/main/
│   ├── java/com/example/elklogging/
│   │   ├── config/                         # LoggingProperties + @EnableConfigurationProperties
│   │   ├── controller/                     # /books, /authors REST endpoints
│   │   ├── filter/HttpLoggingFilter.java   # MDC trace & correlation IDs, exchange logging
│   │   ├── model/                          # Book, Author JPA entities
│   │   ├── repository/                     # Spring Data JPA repos
│   │   └── service/                        # Business logic that emits logs
│   └── resources/
│       ├── application.yml                 # service.name + logging.http.* props
│       ├── data.sql                        # seed data for H2
│       └── logback-spring.xml              # JSON encoder + rolling file appender
├── docker-compose.yml                      # ES + Logstash + Kibana
├── pom.xml
└── README.md
```

---

## How it works (short version)

1. The app calls `log.info(...)` from anywhere in its code.
2. `HttpLoggingFilter` (running first in the servlet chain) has already put `traceId` and `correlationId` into MDC.
3. Logback's `LogstashEncoder` serializes the event as JSON, automatically including MDC entries, and renames its built-in fields to ECS via `<fieldNames>`.
4. The JSON line is written to `./logs/spring-boot-elk-logging.log`.
5. Logstash tails the file, lifts the JSON, **renames the remaining flat fields to ECS paths**, converts numeric types, and POSTs to Elasticsearch.
6. Kibana reads from Elasticsearch and lets you query via KQL.

---

