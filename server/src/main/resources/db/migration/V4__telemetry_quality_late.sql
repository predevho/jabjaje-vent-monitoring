-- 측정값 품질과 늦은 측정값 표시.
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#3)

-- 품질: OK, OUT_OF_RANGE(범위 밖), CLOCK_SKEW(시계 오차). 지금 상태 판단과 최신값은 OK만 쓴다 (스펙 5.2)
ALTER TABLE telemetry
    ADD COLUMN quality VARCHAR(20) NOT NULL DEFAULT 'OK'
        CHECK (quality IN ('OK', 'OUT_OF_RANGE', 'CLOCK_SKEW'));

-- 늦은 측정값: 연결이 끊긴 동안 모아 두었다가 나중에 보낸 것 (ADR-0003)
ALTER TABLE telemetry
    ADD COLUMN late BOOLEAN NOT NULL DEFAULT false;

-- 장비별 최신 OK 측정값 조회용 부분 인덱스
CREATE INDEX idx_telemetry_device_measured_ok ON telemetry (device_id, measured_at DESC) WHERE quality = 'OK';
