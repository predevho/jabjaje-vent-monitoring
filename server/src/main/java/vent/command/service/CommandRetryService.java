package vent.command.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vent.command.domain.RemoteCommand;
import vent.command.domain.type.RemoteCommandStatus;
import vent.command.repository.RemoteCommandRepository;
import vent.command.rule.CommandRetryInput;
import vent.command.rule.CommandRetryPolicy;
import vent.site.repository.DeviceRepository;

/**
 * 원격 명령 재시도·시간 초과 유스케이스 (스펙 7.1 7단계, #9).
 *
 * <p>응답을 기다리는 명령을 하나씩 잠가서 다시 확인하고, {@link CommandRetryPolicy}대로 재시도하거나 시간 초과로 바꾼다.
 * 명령마다 트랜잭션을 따로 써서 하나가 실패해도 나머지는 처리된다. 재시도 발행은 기록이 커밋된 뒤에 하고(#8과 같은 이유),
 * 시간 초과는 같은 트랜잭션에서 {@link RemoteCommandTimedOutEvent}를 발행해 COMMAND_TIMEOUT 이상과 함께 저장한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
@Service
public class CommandRetryService {

    private static final Logger log = LoggerFactory.getLogger(CommandRetryService.class);

    private final RemoteCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final CommandRetryPolicy policy;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public CommandRetryService(RemoteCommandRepository commandRepository, DeviceRepository deviceRepository,
                               CommandRetryPolicy policy, ApplicationEventPublisher eventPublisher,
                               PlatformTransactionManager transactionManager) {
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.policy = policy;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 응답을 기다리는 명령을 모두 확인한다.
     *
     * @param now 확인 시각. 스케줄러는 지금 시각을, 테스트는 원하는 시각을 넣는다
     */
    public void checkPending(OffsetDateTime now) {
        for (UUID commandId : commandRepository.findIdsByStatus(RemoteCommandStatus.SENT)) {
            try {
                transactionTemplate.executeWithoutResult(status -> commandRepository.findByIdForUpdate(commandId)
                        .filter(command -> command.getStatus() == RemoteCommandStatus.SENT)
                        .ifPresent(command -> check(command, now)));
            } catch (RuntimeException e) {
                log.error("원격 명령 재시도 확인 실패, 다음 확인에서 다시 시도: {}", commandId, e);
            }
        }
    }

    private void check(RemoteCommand command, OffsetDateTime now) {
        switch (policy.decide(new CommandRetryInput(command.getRetryCount(), command.getSentAt(), command.getRetriedAt(),
                command.getExpiresAt()), now)) {
            case WAIT -> {
                // 아직 기다린다.
            }
            case RETRY -> {
                command.markRetried(now);
                Long siteId = deviceRepository.findById(command.getDeviceId()).orElseThrow().getSite().getId();
                eventPublisher.publishEvent(new RemoteCommandIssuedEvent(command.getId(), siteId, command.getDeviceId(),
                        command.getParams(), command.getRequestedAt(), command.getExpiresAt(), true));
            }
            case TIME_OUT -> {
                command.markTimedOut();
                log.warn("원격 명령 시간 초과: {} {}", command.getDeviceId(), command.getId());
                eventPublisher.publishEvent(new RemoteCommandTimedOutEvent(command.getId(), command.getDeviceId(), now));
            }
        }
    }
}
