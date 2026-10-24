-- 원격 명령 신뢰성: 시간 초과·밀려남 상태, 만료 시각, 재시도, 늦은 응답 (스펙 7.1·7.2, ADR-0002).
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#9)

-- 상태에 시간 초과(TIMEOUT), 새 명령에 밀림(SUPERSEDED)을 더한다. V9에서 이름을 붙여 둔 제약을 지우고 다시 만든다.
ALTER TABLE command DROP CONSTRAINT ck_command_status;
ALTER TABLE command ADD CONSTRAINT ck_command_status
    CHECK (status IN ('SENT', 'APPLIED', 'REJECTED', 'REJECTED_OFFLINE', 'TIMEOUT', 'SUPERSEDED'));

-- 만료 시각: 보낸 명령에만 있다. 재시도해도 바뀌지 않는다.
ALTER TABLE command ADD COLUMN expires_at TIMESTAMPTZ;
-- #8에서 보낸 명령은 발행 + 60초로 채운다
UPDATE command SET expires_at = sent_at + INTERVAL '60 seconds' WHERE sent_at IS NOT NULL;
ALTER TABLE command ADD CONSTRAINT ck_command_expires_at CHECK ((sent_at IS NULL) = (expires_at IS NULL));

-- 재시도 횟수와 마지막 재시도 시각
ALTER TABLE command ADD COLUMN retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0);
ALTER TABLE command ADD COLUMN retried_at TIMESTAMPTZ;
ALTER TABLE command ADD CONSTRAINT ck_command_retried_at CHECK ((retry_count = 0) = (retried_at IS NULL));

-- 시간 초과 뒤에 온 응답인지. 응답이 반영된 명령(적용됨, 거절됨)에만 참일 수 있다.
ALTER TABLE command ADD COLUMN late_ack BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE command ADD CONSTRAINT ck_command_late_ack CHECK (NOT late_ack OR status IN ('APPLIED', 'REJECTED'));

-- #8 때 응답 없이 남은 명령 중 이미 만료된 것은 시간 초과로 닫는다. 첫 검사에서 장비마다 COMMAND_TIMEOUT이 열리지 않게.
UPDATE command SET status = 'TIMEOUT' WHERE status = 'SENT' AND expires_at < now();

-- 2초 주기 검사가 응답을 기다리는 명령만 찾는다
CREATE INDEX idx_command_pending ON command (sent_at) WHERE status = 'SENT';
-- 장비당 응답을 기다리는 명령은 하나 (ADR-0002). 서비스는 장비 행을 잠가 지키고, DB도 한 번 더 막는다.
CREATE UNIQUE INDEX uq_command_pending_per_device ON command (device_id) WHERE status = 'SENT';
