# 패시브하우스 환기 모니터링 MVP

현장에 설치된 열회수 환기장치와 VEMC의 상태를 원격으로 지켜보고, 문제(이상)를 먼저 알아채 A/S를 돕는 서비스의 MVP다.
설계는 [docs/spec.md](docs/spec.md), 용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

## 실행

Docker만 있으면 된다. 저장소 루트에서 아래 명령 하나로 MQTT 브로커(Mosquitto), PostgreSQL, 서버 앱(화면 포함), 가상 게이트웨이(시뮬레이터)가 함께 뜬다. 처음에는 이미지와 의존성을 내려받느라 몇 분 걸린다.

```bash
docker compose up -d --build
```

- 대시보드: <http://localhost:8080/> (현장 3곳의 최신 측정값, 30초마다 갱신)
- 현장 목록 API: <http://localhost:8080/api/sites>
- 장비별 최신 측정값 API: <http://localhost:8080/api/telemetry/latest>
- 시뮬레이터 로그: `docker compose logs -f simulator`

종료할 때는 `docker compose down`, 데이터까지 지우려면 `docker compose down -v`.

## 개발

- 서버 테스트: `./gradlew :server:test` (Docker가 켜져 있어야 한다. 테스트가 PostgreSQL과 Mosquitto 컨테이너를 직접 띄운다)
- 빌드 도구는 Gradle 9, 코드는 Java 17 툴체인으로 컴파일한다. JDK 17이 없으면 Gradle이 자동으로 받는다.
- 화면 개발: `docker compose up -d mosquitto postgres` → `./gradlew :server:bootRun` → `cd web && npm install && npm run dev` (Vite가 `/api`를 8080으로 넘긴다)
- 시뮬레이터만 실행: `./gradlew :simulator:run` (환경변수 `MQTT_URI`, `INTERVAL_SECONDS`)

## 구조

```
server/      Spring Boot 앱 (패키지 vent/<기능>/controller·service·repository·domain·dto)
simulator/   가상 게이트웨이 (순수 Java, Paho MQTT 5)
web/         React + TypeScript + Vite 화면 (서버 이미지에 함께 들어간다)
infra/       브로커 설정
docs/        스펙, ADR, 면접 준비 문서
```
