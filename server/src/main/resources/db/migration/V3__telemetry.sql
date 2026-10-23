-- 측정값 테이블과 장비의 마지막 수신 시각.
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-23 (#2)

-- 이 장비의 측정값을 서버가 마지막으로 받은 시각 (스펙 5장)
ALTER TABLE device ADD COLUMN last_received_at TIMESTAMPTZ;

-- 측정값: 게이트웨이가 30초마다 보내는 환기장치 상태 수치 (GLOSSARY: 측정값)
-- 품질(quality)과 늦은 측정값(late) 컬럼은 #3에서 추가한다.
CREATE TABLE telemetry (
    id           BIGSERIAL    PRIMARY KEY,
    device_id    VARCHAR(50)  NOT NULL REFERENCES device (id),
    -- QoS 1은 같은 메시지가 두 번 올 수 있어 messageId로 중복을 막는다 (스펙 4.3)
    message_id   UUID         NOT NULL UNIQUE,
    measured_at  TIMESTAMPTZ  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL,
    co2_ppm      INT          NOT NULL,
    temp_c       NUMERIC(4, 1) NOT NULL,
    humidity_pct NUMERIC(4, 1) NOT NULL,
    supply_cmh   INT          NOT NULL,
    exhaust_cmh  INT          NOT NULL,
    filter_pa    INT          NOT NULL,
    bypass       BOOLEAN      NOT NULL
);

-- 장비별 최신 측정값과 기간 조회용 (스펙 5장)
CREATE INDEX idx_telemetry_device_measured ON telemetry (device_id, measured_at DESC);
