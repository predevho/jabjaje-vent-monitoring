package vent.command.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import vent.command.domain.RemoteCommand;
import vent.command.domain.type.RemoteCommandStatus;
import vent.command.domain.type.RemoteCommandType;

/**
 * 원격 명령 응답 (#8).
 *
 * @param commandId    명령 ID
 * @param deviceId     대상 장비
 * @param type         종류
 * @param status       상태 (보냄, 적용됨, 거절됨, 연결이 끊겨 보내지 않음)
 * @param supplyCmh    부스트 급기
 * @param exhaustCmh   부스트 배기
 * @param durationSec  지속시간 (초)
 * @param rejectReason 장비가 거절한 이유. 없으면 null
 * @param requestedAt  요청 시각
 * @param sentAt       보낸 시각. 보내지 않았으면 null
 * @param ackedAt      장비 응답 시각. 아직 없으면 null
 * @param notice       화면 안내. 게이트웨이가 연결되지 않아 보내지 않았을 때만 (스펙 7.1)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record RemoteCommandResponse(
        UUID commandId,
        String deviceId,
        RemoteCommandType type,
        RemoteCommandStatus status,
        int supplyCmh,
        int exhaustCmh,
        int durationSec,
        String rejectReason,
        OffsetDateTime requestedAt,
        OffsetDateTime sentAt,
        OffsetDateTime ackedAt,
        String notice) {

    static final String NOT_CONNECTED_NOTICE = "지금은 게이트웨이가 연결되지 않아 명령을 보낼 수 없어요";

    public static RemoteCommandResponse from(RemoteCommand command) {
        return new RemoteCommandResponse(
                command.getId(),
                command.getDeviceId(),
                command.getType(),
                command.getStatus(),
                command.getParams().supplyCmh(),
                command.getParams().exhaustCmh(),
                command.getParams().durationSec(),
                command.getRejectReason(),
                command.getRequestedAt(),
                command.getSentAt(),
                command.getAckedAt(),
                command.getStatus() == RemoteCommandStatus.REJECTED_OFFLINE ? NOT_CONNECTED_NOTICE : null);
    }
}
