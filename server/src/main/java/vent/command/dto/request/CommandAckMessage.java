package vent.command.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 장비가 보내는 원격 명령 응답 ({@code jabjaje/{현장ID}/{장비ID}/command/ack}, 스펙 4.2).
 *
 * <p>모르는 필드는 무시한다(ADR-0004).
 *
 * @param schemaVersion 메시지 형식 버전
 * @param commandId     응답하는 명령의 ID
 * @param result        {@code APPLIED} 또는 {@code REJECTED}
 * @param reason        거절 사유 (예: {@code FROST_PROTECTION}). 적용이면 없음
 * @param appliedAt     장비가 적용한 시각. 거절이면 없음
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandAckMessage(Integer schemaVersion, UUID commandId, String result, String reason,
                                OffsetDateTime appliedAt) {

    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
}
