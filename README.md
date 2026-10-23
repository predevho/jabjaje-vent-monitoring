# 패시브하우스 환기 모니터링 MVP

현장에 설치된 열회수 환기장치와 VEMC의 상태를 원격으로 지켜보고, 문제(이상)를 먼저 알아채 A/S를 돕는 서비스의 MVP다.
설계는 [docs/spec.md](docs/spec.md), 용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

## 실행

Docker만 있으면 된다. 저장소 루트에서 아래 명령 하나로 MQTT 브로커(Mosquitto), PostgreSQL, 서버 앱이 함께 뜬다. 처음에는 이미지와 의존성을 내려받느라 몇 분 걸린다.

```bash
docker compose up -d --build
```

현장 목록 확인: <http://localhost:8080/api/sites>

종료할 때는 `docker compose down`, 데이터까지 지우려면 `docker compose down -v`.

## 개발

- 서버 테스트: `./gradlew :server:test` (Docker가 켜져 있어야 한다. 테스트가 PostgreSQL 컨테이너를 직접 띄운다)
- 빌드 도구는 Gradle 9, 코드는 Java 17 툴체인으로 컴파일한다. JDK 17이 없으면 Gradle이 자동으로 받는다.

## 구조

```
server/      Spring Boot 앱 (패키지 vent, 기능별 하위 패키지)
simulator/   가상 게이트웨이 (#2에서 추가)
infra/       브로커 설정
docs/        스펙, ADR, 면접 준비 문서
```
