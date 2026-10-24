package vent.command.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.command.domain.RemoteCommand;
import vent.command.dto.request.CommandAckMessage;
import vent.command.repository.RemoteCommandRepository;
import vent.site.repository.DeviceRepository;

/**
 * 원격 명령 응답 처리 유스케이스 (스펙 7.1 6단계, #8).
 *
 * <p>장비가 보낸 적용·거절 응답을 명령 상태에 반영한다. 토픽의 장비·현장이 명령의 장비와 다르면 버리고(장비 신원은 토픽, 스펙 4.3),
 * 이미 끝난 명령의 응답(QoS 1 중복)과 밀려난 명령의 응답은 무시한다. 시간 초과 뒤 늦게 온 응답은 실제 결과로 바꾸고
 * 늦은 응답으로 표시한다(#9). 반영하면 같은 트랜잭션에서 {@link RemoteCommandAnsweredEvent}를 발행해 COMMAND_TIMEOUT을 해제한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Service
public class CommandAckService {

    private static final Logger log = LoggerFactory.getLogger(CommandAckService.class);

    private final RemoteCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CommandAckService(RemoteCommandRepository commandRepository, DeviceRepository deviceRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 응답 하나를 반영한다.
     *
     * @param topicSiteId   토픽의 현장 ID
     * @param topicDeviceId 토픽의 장비 ID
     * @param message       응답 메시지
     * @param receivedAt    서버가 받은 시각
     */
    @Transactional
    public CommandAckResult apply(String topicSiteId, String topicDeviceId, CommandAckMessage message,
                                  OffsetDateTime receivedAt) {
        if (message.commandId() == null) {
            return CommandAckResult.INVALID;
        }
        Optional<RemoteCommand> found = commandRepository.findByIdForUpdate(message.commandId());
        if (found.isEmpty()) {
            return CommandAckResult.UNKNOWN_COMMAND;
        }
        RemoteCommand command = found.get();
        boolean sameDevice = command.getDeviceId().equals(topicDeviceId)
                && deviceRepository.findById(topicDeviceId).map(device -> device.belongsTo(topicSiteId)).orElse(false);
        if (!sameDevice) {
            return CommandAckResult.DEVICE_MISMATCH;
        }

        boolean changed;
        if (CommandAckMessage.APPLIED.equals(message.result())) {
            changed = command.markApplied(receivedAt);
        } else if (CommandAckMessage.REJECTED.equals(message.result())) {
            changed = command.markRejected(message.reason(), receivedAt);
        } else {
            return CommandAckResult.INVALID;
        }
        if (!changed) {
            return CommandAckResult.ALREADY_FINAL;
        }
        log.info("원격 명령 응답: {} {} {}{}", topicDeviceId, command.getStatus(),
                message.reason() == null ? "" : "(" + message.reason() + ")", command.isLateAck() ? " 늦은 응답" : "");
        eventPublisher.publishEvent(new RemoteCommandAnsweredEvent(command.getId(), topicDeviceId, command.isLateAck(), receivedAt));
        return CommandAckResult.RECORDED;
    }
}
