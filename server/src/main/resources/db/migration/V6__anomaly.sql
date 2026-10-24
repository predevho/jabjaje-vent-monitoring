-- 이상 테이블.
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#4)

-- 이상: 규칙이 측정값이나 연결 상태에서 감지한 문제 (GLOSSARY: 이상)
CREATE TABLE anomaly (
    id          BIGSERIAL   PRIMARY KEY,
    -- 대상은 장비 또는 게이트웨이 중 하나 (게이트웨이 이상은 #6)
    device_id   VARCHAR(50) REFERENCES device (id),
    gateway_id  VARCHAR(50) REFERENCES gateway (id),
    rule        VARCHAR(40) NOT NULL,
    severity    VARCHAR(10) NOT NULL CHECK (severity IN ('INFO', 'WARN', 'CRITICAL')),
    status      VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    message     TEXT        NOT NULL,
    -- 판단 근거가 된 측정값의 측정 시각 (ADR-0003)
    opened_at   TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CHECK ((device_id IS NULL) <> (gateway_id IS NULL)),
    CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

-- 같은 대상, 같은 규칙으로 열린 이상은 하나만 (스펙 5장).
-- 대상 칸마다 따로 만든다. 장비 ID와 게이트웨이 ID가 우연히 같아도 부딪히지 않고, 열린 이상 조회(device_id, rule)에도 쓰인다.
CREATE UNIQUE INDEX uq_anomaly_open_device_rule
    ON anomaly (device_id, rule) WHERE status = 'OPEN' AND device_id IS NOT NULL;
CREATE UNIQUE INDEX uq_anomaly_open_gateway_rule
    ON anomaly (gateway_id, rule) WHERE status = 'OPEN' AND gateway_id IS NOT NULL;

-- 열린 이상 목록 조회용
CREATE INDEX idx_anomaly_status_opened ON anomaly (status, opened_at DESC);
