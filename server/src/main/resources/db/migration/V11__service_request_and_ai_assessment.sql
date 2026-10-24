-- A/S 신청과 AI 소견 테이블 (스펙 5장, 8장, ADR-0005).
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#10)

-- A/S 신청: 고객이 불편이나 고장을 알려 와 접수된 한 건 (용어집)
CREATE TABLE service_request (
    id                    BIGSERIAL   PRIMARY KEY,
    site_id               BIGINT      NOT NULL REFERENCES site (id),
    -- 고객이 장비를 특정하지 못할 수 있다
    device_id             VARCHAR(50) REFERENCES device (id),
    customer_message      TEXT        NOT NULL,
    -- 고객 이름·연락처는 직원만 본다. AI에는 보내지 않는다 (스펙 8.2)
    customer_name         VARCHAR(50),
    contact_phone         VARCHAR(30),
    status                VARCHAR(20) NOT NULL CONSTRAINT ck_service_request_status
        CHECK (status IN ('RECEIVED', 'ASSESSED', 'NEEDS_HUMAN', 'DECIDED')),
    created_at            TIMESTAMPTZ NOT NULL,
    -- 백그라운드에서 AI 소견을 맡은 시각. 두 곳(접수 직후 처리, 재처리 스케줄러)이 같은 건을 동시에 처리하지 않게 한다
    assessment_started_at TIMESTAMPTZ
);

CREATE INDEX idx_service_request_created ON service_request (created_at DESC);
-- 재처리 스케줄러가 접수된 채 남은 건만 찾는다
CREATE INDEX idx_service_request_received ON service_request (created_at) WHERE status = 'RECEIVED';

-- AI 소견: AI가 A/S 신청에 대해 낸 원인과 조치의 초안. 시도마다 한 행 (재시도 기록을 남긴다)
CREATE TABLE ai_assessment (
    id                 BIGSERIAL   PRIMARY KEY,
    service_request_id BIGINT      NOT NULL REFERENCES service_request (id),
    attempt            INT         NOT NULL CHECK (attempt >= 1),
    -- AI에 보낸 입력 사본 (ADR-0005). 고객 이름·연락처는 없다
    input_snapshot     JSONB       NOT NULL,
    -- JSON으로 읽힌 소견. 형식 오류면 비어 있고 raw_response에 원문이 남는다
    result             JSONB,
    raw_response       TEXT,
    validation_errors  JSONB       NOT NULL DEFAULT '[]',
    passed             BOOLEAN     NOT NULL,
    model              VARCHAR(60) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    UNIQUE (service_request_id, attempt)
);
