package vent.servicerequest.rule;

import java.time.OffsetDateTime;

/**
 * 상황 요약에 넣는 이상 하나 (#10).
 *
 * @param rule     규칙 이름 (예: FILTER_CLOGGED)
 * @param severity 심각도
 * @param status   열림 또는 해결
 * @param openedAt 발생 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationAnomaly(String rule, String severity, String status, OffsetDateTime openedAt) {
}
