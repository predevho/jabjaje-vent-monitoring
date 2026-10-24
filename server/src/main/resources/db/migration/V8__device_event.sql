-- 장비 이벤트 테이블 (VEMC의 후드 ON/OFF와 후드 연동 확인 결과).
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#7)

CREATE TABLE device_event (
    id               BIGSERIAL   PRIMARY KEY,
    device_id        VARCHAR(50) NOT NULL REFERENCES device (id),
    -- QoS 1로 같은 이벤트가 두 번 올 수 있어 messageId로 중복을 막는다 (스펙 4.3)
    message_id       UUID        NOT NULL UNIQUE,
    type             VARCHAR(20) NOT NULL CHECK (type IN ('HOOD_ON', 'HOOD_OFF')),
    -- 장비에서 일어난 시각. 확인은 이 시각 기준이다 (ADR-0003)
    occurred_at      TIMESTAMPTZ NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL,
    -- 후드 연동 확인 결과. 비어 있으면 아직 확인 전 (스펙 6.2)
    interlock_result VARCHAR(20) CHECK (interlock_result IN ('CONFIRMED', 'NO_RESPONSE', 'INCONCLUSIVE')),
    judged_at        TIMESTAMPTZ,
    CHECK ((interlock_result IS NULL) = (judged_at IS NULL)),
    -- 확인은 HOOD_ON에만 한다
    CHECK (type = 'HOOD_ON' OR interlock_result IS NULL)
);

-- 30초 주기 검사가 확인 전 HOOD_ON만 찾는다
CREATE INDEX idx_device_event_pending ON device_event (occurred_at)
    WHERE type = 'HOOD_ON' AND interlock_result IS NULL;
-- HOOD_ON 뒤 처음 온 HOOD_OFF를 찾는다
CREATE INDEX idx_device_event_device_occurred ON device_event (device_id, occurred_at);
