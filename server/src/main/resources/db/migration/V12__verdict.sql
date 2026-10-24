-- 판정 테이블 (스펙 5장, 8.1, ADR-0005).
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#11)

-- 판정: 직원이 AI 소견을 보고 확정한 원인, 조치, 고객 안내 (용어집). A/S 신청당 하나
-- 항목은 AI 소견과 같은 구조로 나눠 두어 어느 항목을 고쳤는지 비교할 수 있게 한다
CREATE TABLE verdict (
    id                   BIGSERIAL   PRIMARY KEY,
    service_request_id   BIGINT      NOT NULL UNIQUE REFERENCES service_request (id),
    -- 참고한 AI 소견. 사람 확인 필요 건을 처음부터 판정하면 비어 있다
    ai_assessment_id     BIGINT      REFERENCES ai_assessment (id),
    causes               JSONB       NOT NULL,
    technician_actions   JSONB       NOT NULL,
    parts                JSONB       NOT NULL,
    needs_visit          BOOLEAN     NOT NULL,
    recommended_services JSONB       NOT NULL,
    customer_reply       TEXT        NOT NULL,
    -- AI 소견을 고쳤는지와 고친 항목 (서버가 비교해서 정한다)
    edited_from_ai       BOOLEAN     NOT NULL,
    edited_fields        JSONB       NOT NULL DEFAULT '[]',
    decided_by           VARCHAR(50) NOT NULL,
    decided_at           TIMESTAMPTZ NOT NULL
);
