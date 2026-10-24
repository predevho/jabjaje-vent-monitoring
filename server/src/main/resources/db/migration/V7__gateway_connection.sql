-- 게이트웨이 연결 상태 컬럼 (마지막 수신 시각, 끊긴 시각, 보고한 버전·지원 기능).
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#6)

-- 서버가 이 게이트웨이에서 마지막으로 받은 시각 (측정값 저장, retained가 아닌 online 보고)
ALTER TABLE gateway ADD COLUMN last_received_at TIMESTAMPTZ;
-- 연결이 끊긴 시각. 끊긴 것을 알아챈 시각이 아니라 마지막 수신 시각을 넣는다 (스펙 6.3)
ALTER TABLE gateway ADD COLUMN disconnected_at TIMESTAMPTZ;
-- online 상태 메시지로 보고한 버전과 지원 기능 (스펙 4.4)
ALTER TABLE gateway ADD COLUMN firmware_version VARCHAR(30);
ALTER TABLE gateway ADD COLUMN capabilities JSONB;

-- 일시 끊김·오프라인이면 끊긴 시각이 있고, 연결됨·연결 전이면 없다
ALTER TABLE gateway ADD CONSTRAINT ck_gateway_disconnected_at
    CHECK ((connection_state IN ('INTERRUPTED', 'OFFLINE')) = (disconnected_at IS NOT NULL));

-- 무수신 검사가 연결됨·일시 끊김 게이트웨이만 찾는다
CREATE INDEX idx_gateway_connection_state ON gateway (connection_state);
