# HealthSafe

## Overview

Hospital ward status and emergency staffing schedules.

Domain entities: wards, wings, specialist departments.

Every class in this repo lives in a single flat package, `co.wethinkcode.healthsafe`. HealthSafe is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`wards-outdated.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`staffing-events-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **EquipmentAlertServiceApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7030 | Parses and cleans `wards-outdated.csv` |
| WardServiceApp | [`ward-service/`](ward-service) | 7031 | Provides lists of wards and departments. |
| AlertLevelServiceApp | [`alert-level-service/`](alert-level-service) | 7032 | Tracks the hospital Emergency Status (0-8, 8 = full Code Blue). |
| StaffingServiceApp | [`staffing-service/`](staffing-service) | 7033 | Provides on-call schedules for doctors based on ward and status. |
| EquipmentAlertServiceApp | [`equipment-alert-service/`](equipment-alert-service) | 7034 | uses a Queue to guarantee delivery of critical medical equipment failure alerts. |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `staffing-events-topic`: Staffing updates are broadcast as Events via the broker to decouple the frontend from the Staffing Service.

**Status:** all four stages are implemented — ingestion, the three REST services,
the staffing topic and the equipment failure queue.

## How it works

### 1. Ingestion — cleaning legacy data

`ingestion-service` reads `wards-outdated.csv` with OpenCSV and cleans every row
(`Data.java`):

- text is trimmed, repeated spaces collapsed and lowercased (`" East Wing "` → `east wing`)
- placeholders (`N/A`, `-`, `tbd`, `unknown`, ...) become `null`
- `beds_available` keeps only a non-negative number (`-1`, `five` → `null`)
- duplicate ward IDs (`W-05` / `w-05`) are dropped, keeping the first

The cleaned records are served at `GET /wards`.

### 2. REST services

- **ward-service** fetches the cleaned wards from ingestion over HTTP and serves
  ward and department lookups (`DataRetrieval.java`).
- **alert-level-service** holds the Emergency Status in memory and rejects any level
  outside 0-8 with `400`.
- **staffing-service** builds an on-call schedule (`WardData.java`): it calls
  ward-service for the ward, then alert-level-service for the current level, and picks
  `2 + level` doctors from `doctors.csv` in that ward's department. Downstream failures
  return clean errors instead of crashing: `503` service down, `404` unknown ward,
  `409` no beds available, `502` unexpected response.

### 3. Topic — `staffing-events-topic`

Every schedule staffing-service builds is also published to the topic.
ward-service subscribes and keeps the latest update, served at
`GET /staffing-updates`. The publisher doesn't know who is listening, so the two
services are decoupled.

### 4. Queue — `equipment-failure-queue`

ward-service publishes equipment failures to the queue: the four samples in
`equipment_failure_payload.json` on startup, plus any sent to
`POST /equipment-failure`. Messages are `PERSISTENT`, and equipment-alert-service
uses `CLIENT_ACKNOWLEDGE`, acknowledging each message only after it has handled it.
An alert sent while the consumer is down waits on the queue, and one that fails
mid-handling is redelivered — no alert is lost.

## Integration contracts

| From | To | Call | Purpose |
|---|---|---|---|
| `ward-service` | `ingestion-service` | `GET /wards` → cleaned ward records | Populate its own ward/department list |
| `staffing-service` | `ward-service` | `GET /wards/{id}` → `404` if unknown | Validate the ward before scheduling |
| `staffing-service` | `alert-level-service` | `GET /alert-level` → `{ "level": 0-8 }` | Read current Emergency Status to size the on-call schedule |
| `staffing-service` | `ward-service` (topic, stage 3) | publish to `staffing-events-topic` | Broadcast a schedule/status change |
| `ward-service` (topic, stage 3) | — | subscribe to `staffing-events-topic` | React to staffing updates without polling |
| `ward-service` (queue, stage 4) | `equipment-alert-service` | publish to `equipment-failure-queue` | Guarantee delivery of an equipment failure alert |

## Endpoints

Every service also exposes `GET /health` → `OK`.

| Service | Method | Path | Returns |
|---|---|---|---|
| ingestion-service | GET | `/wards` | Cleaned ward records |
| ward-service | GET | `/wards` | All wards |
| ward-service | GET | `/wards/{id}` | One ward, `404` if unknown (case-insensitive) |
| ward-service | GET | `/departments` | Sorted list of departments |
| ward-service | GET | `/staffing-updates` | Latest staffing event from the topic, `204` if none yet |
| ward-service | POST | `/equipment-failure` | Publishes the failure to the queue, `202` |
| alert-level-service | GET | `/alert-level` | `{ "level": 0-8 }` |
| alert-level-service | PUT / POST | `/alert-level` | Sets the level, `400` outside 0-8 |
| staffing-service | GET | `/staffing/{wardId}` | On-call schedule for the ward |

## Project structure

```
healthsafe/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7030)
│   └── src/main/
│       ├── java/.../IngestionServiceApp.java, Data.java
│       └── resources/wards-outdated.csv
├── ward-service/               (port 7031)
│   └── src/main/
│       ├── java/.../WardServiceApp.java, DataRetrieval.java, JsonReader.java,
│       │            EquipmentFailurePayload.java, mq/MqConfig.java
│       └── resources/equipment_failure_payload.json
├── alert-level-service/        (port 7032)
│   └── src/main/java/.../AlertLevelServiceApp.java
├── staffing-service/           (port 7033)
│   └── src/main/
│       ├── java/.../StaffingServiceApp.java, WardData.java, Schedule.java, mq/MqConfig.java
│       └── resources/doctors.csv
├── equipment-alert-service/    (port 7034)
│   └── src/main/java/.../EquipmentAlertServiceApp.java, mq/MqConfig.java
└── common/
    ├── docker-compose.yml
    └── README.md
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every service folder is an **independent** Maven project — there is no
parent/aggregator pom. Build each one from the project root:

```
cd ~/sin-000-healthsafe/ingestion-service && mvn -q -DskipTests package
cd ~/sin-000-healthsafe/ward-service && mvn -q -DskipTests package
cd ~/sin-000-healthsafe/alert-level-service && mvn -q -DskipTests package
cd ~/sin-000-healthsafe/staffing-service && mvn -q -DskipTests package
cd ~/sin-000-healthsafe/equipment-alert-service && mvn -q -DskipTests package
```

## Run

### One command

```
./demo.sh        # builds missing jars, starts the broker, opens a terminal tab per service
./demo.sh stop   # stops every service and the broker
```

`demo.sh` opens one GNOME Terminal window with a tab for the broker logs, each service,
an `equipment` tab that waits for Enter (so the queued alerts can be seen arriving), and
a `demo` tab that runs the [Try it](#try-it) commands one at a time.

### Manually

Start the broker first, then each service in its own terminal, in this order.
Every command uses the full path, so it works from any folder.

```
# broker
cd ~/sin-000-healthsafe/common && docker compose up -d

# terminal 1
cd ~/sin-000-healthsafe/ingestion-service && java -jar target/ingestion-service.jar
# terminal 2
cd ~/sin-000-healthsafe/alert-level-service && java -jar target/alert-level-service.jar
# terminal 3
cd ~/sin-000-healthsafe/staffing-service && java -jar target/staffing-service.jar
# terminal 4
cd ~/sin-000-healthsafe/ward-service && java -jar target/ward-service.jar
# terminal 5
cd ~/sin-000-healthsafe/equipment-alert-service && java -jar target/equipment-alert-service.jar
```

To start with an empty queue, run `docker compose down` before `docker compose up -d`.

Stop: `Ctrl+C` in each service terminal, then
`cd ~/sin-000-healthsafe/common && docker compose down`.

## Try it

```
# cleaned data
curl -s localhost:7030/wards | jq
curl -s localhost:7031/wards/W-01 | jq
curl -s localhost:7031/departments | jq

# alert level: 9 is rejected, 3 is accepted
curl -s -X PUT localhost:7032/alert-level -H 'Content-Type: application/json' -d '{"level":9}'
curl -s -X PUT localhost:7032/alert-level -H 'Content-Type: application/json' -d '{"level":3}'

# staffing schedule (calls ward-service and alert-level-service, then publishes to the topic)
curl -s localhost:7033/staffing/W-01 | jq

# the same schedule, received by ward-service through the topic
curl -s localhost:7031/staffing-updates | jq

# error handling: no beds, unknown ward
curl -s localhost:7033/staffing/W-02
curl -s localhost:7033/staffing/W-99

# equipment failure through the queue (shows up in the equipment-alert-service terminal)
curl -s -X POST localhost:7031/equipment-failure -H 'Content-Type: application/json' \
  -d '{"wardId":"W-06","equipment":"MRI Scanner MRI-01","severity":"CRITICAL","time":"2026-11-12T18:00:00Z"}'
```

To see guaranteed delivery, start ward-service before equipment-alert-service: the
four sample alerts wait on the queue and arrive as soon as the consumer starts.

## Known limitations

- Data cleaning doesn't merge spelling variants: `paediatrics` and `pediatrics` are
  listed as separate departments.
- Bed counts aren't range-checked, so ward `w-13` keeps an unrealistic `2023` beds.
- The alert level and the latest staffing event are held in memory and reset when
  their service restarts.
- ward-service's topic subscription isn't durable: staffing updates published while
  it is down are not replayed.

## Test

There are no automated tests. Each running service exposes `/health`, so
sanity-check manually:

```
curl http://localhost:7030/health   # -> OK
```

To add tests to a module, add JUnit 5 and Surefire to its `pom.xml`:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>
```

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
</plugin>
```

then add tests under that module's `src/test/java/...` and run:

```
mvn test
```

WTC-S7F8Z2RU
