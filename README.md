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
- 화면 개발: `docker compose up -d mosquitto postgres` → `VENT_MQTT_USERNAME=vent-server VENT_MQTT_PASSWORD=vent-server-demo ./gradlew :server:bootRun` → `cd web && npm install && npm run dev` (Vite가 `/api`를 8080으로 넘긴다). 브로커가 익명 접속을 거부하므로 서버 계정을 넣는다(#13)
- 시뮬레이터만 실행: `./gradlew :simulator:run` (환경변수 `MQTT_URI`, `INTERVAL_SECONDS`, `MQTT_PASSWORD_GW_S1` 등. 비밀번호 기본값은 compose 시연 기본값과 같다)

## 구조

```
server/      Spring Boot 앱 (패키지 vent/<기능>/controller·service·repository·domain·dto)
simulator/   가상 게이트웨이 (순수 Java, Paho MQTT 5)
web/         React + TypeScript + Vite 화면 (서버 이미지에 함께 들어간다)
infra/       브로커 설정
docs/        스펙, ADR, 면접 준비 문서
```

## 보안 (#13, 스펙 9.2)

### 지금 한 것
- **익명 접속 거부**: 게이트웨이와 서버는 브로커 계정으로 접속한다(`infra/mosquitto/mosquitto.conf`).
- **현장별 토픽 권한(ACL, 최소 권한)**: 게이트웨이(`gw-s1`)는 자기 현장(`jabjaje/1/...`)의 측정값·이벤트·상태·명령 응답만 쓰고, 자기 현장 원격 명령만 받는다. 상태는 자기 게이트웨이 ID로만 쓴다. 원격 명령은 서버(`vent-server`)만 보낸다(`infra/mosquitto/acl`).
- **브로커·DB 포트는 내 컴퓨터에서만**: `127.0.0.1:1883`, `127.0.0.1:5432`. 시연 비밀번호가 저장소에 공개되어 있어서다.
- **비밀번호는 저장소에 두지 않음**: 브로커가 시작할 때 환경변수로 비밀번호 파일을 만든다(`infra/mosquitto/entrypoint.sh`). `docker-compose.yml`의 기본값(`gw-s1-demo` 등)은 시연용이다.
- **계정 이름 = 클라이언트 ID**(`use_username_as_clientid`): 계정만 있으면 다른 클라이언트 ID로 접속해 서버나 다른 게이트웨이의 연결을 끊을 수 있는 문제를 막는다. 대신 같은 계정으로 두 곳에서 접속하면 먼저 붙은 쪽이 끊긴다. 계정이 새면 그 게이트웨이를 끊을 수 있다는 뜻이라, 계정은 게이트웨이마다 따로 둔다. 서버를 여러 대 띄우면 서버 계정도 인스턴스마다 둔다(공유 구독은 스펙 9.1 C13).
- **장비 신원은 토픽으로**: 서버는 토픽의 장비 ID를 믿고, 메시지의 장비 ID나 현장이 다르면 버린다(스펙 4.3). ACL이 토픽 단위로 권한을 묶기 때문이다.

### 거부 시연
```bash
# 익명 발행 → 거부 (exit 5: not authorized)
docker compose exec mosquitto mosquitto_pub -h localhost -t jabjaje/1/vent-s1/telemetry -m '{}'

# 현장 1 게이트웨이 계정으로 현장 2 토픽에 발행 → PUBACK RC:135(Not authorized), 서버에 저장되지 않음
docker compose exec mosquitto mosquitto_pub -h localhost -V mqttv5 -q 1 -u gw-s1 -P gw-s1-demo \
  -t jabjaje/2/vent-s2/telemetry -m '{}' -d
```
시연 중 두 번째 명령은 같은 계정(`gw-s1`)으로 접속하므로 진짜 `gw-s1` 시뮬레이터 연결이 잠깐 끊겼다가 다시 붙는다.

### 운영에서 할 것 (이 MVP에서는 문서로만)
- **TLS(8883)**: 지금은 로컬 데모라 평문(1883)이다. 운영에서는 브로커에 TLS를 켜고 1883을 닫는다(스펙 9.2 S2).
- **장비별 인증서(TLS 클라이언트 인증)**: 비밀번호 대신 게이트웨이마다 인증서를 발급하고, 인증서의 이름을 계정으로 쓴다(`use_identity_as_username`). 공장 출고나 설치 때 넣고, 잃어버린 장비는 인증서를 폐기한다.
- **비밀 관리**: 비밀번호·인증서 키는 비밀 관리 서비스(예: AWS Secrets Manager, Vault)에서 주입한다.
- **현장이 많아지면**: ACL 파일을 손으로 고치지 않고 Mosquitto dynamic security 플러그인이나 DB 기반 인증 플러그인, 또는 매니지드 IoT 서비스(AWS IoT Core 등)의 정책으로 옮긴다. 설치 등록(QR) 때 계정과 권한이 함께 만들어지게 한다.
