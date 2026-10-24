-- 원격 명령 테이블, 측정값의 부스트 여부.
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#8)

-- 원격 명령: 서버가 게이트웨이를 통해 장비에 보내는 요청 (스펙 5장 command, 7장)
-- 재시도·시간 초과·밀려남·만료 관련 컬럼과 상태(TIMEOUT, SUPERSEDED)는 쓰는 티켓(#9)에서 추가한다.
CREATE TABLE command (
    id            UUID        PRIMARY KEY,
    device_id     VARCHAR(50) NOT NULL REFERENCES device (id),
    type          VARCHAR(20) NOT NULL CHECK (type IN ('BOOST')),
    -- {supplyCmh, exhaustCmh, durationSec}
    params        JSONB       NOT NULL,
    -- 제약에 이름을 붙인다. #9에서 상태(TIMEOUT, SUPERSEDED)와 늦은 응답을 넣을 때 이름으로 지우고 다시 만든다.
    status        VARCHAR(20) NOT NULL CONSTRAINT ck_command_status
        CHECK (status IN ('SENT', 'APPLIED', 'REJECTED', 'REJECTED_OFFLINE')),
    -- 장비가 거절한 이유 (예: FROST_PROTECTION, HOOD_INTERLOCK)
    reject_reason VARCHAR(40),
    requested_by  VARCHAR(20) NOT NULL CHECK (requested_by IN ('USER')),
    requested_at  TIMESTAMPTZ NOT NULL,
    -- 게이트웨이로 보낸 시각. 연결이 끊겨 보내지 않았으면(REJECTED_OFFLINE) 비어 있다
    sent_at       TIMESTAMPTZ,
    -- 장비 응답을 받은 시각
    acked_at      TIMESTAMPTZ,
    CONSTRAINT ck_command_sent_at CHECK ((status = 'REJECTED_OFFLINE') = (sent_at IS NULL)),
    CONSTRAINT ck_command_acked_at CHECK ((status IN ('APPLIED', 'REJECTED')) = (acked_at IS NOT NULL))
);

-- 장비별 최근 명령 조회
CREATE INDEX idx_command_device_requested ON command (device_id, requested_at DESC);

-- 측정값에 부스트 중인지 추가 (목표 풍량 미달 규칙이 부스트 중 측정값을 뺀다, 스펙 6.1)
-- 예전 게이트웨이는 이 필드를 보내지 않으므로 기본값은 false
ALTER TABLE telemetry ADD COLUMN boost BOOLEAN NOT NULL DEFAULT false;
