package vent.command.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.command.domain.RemoteCommand;
import vent.command.dto.request.BoostRequest;
import vent.command.dto.response.RemoteCommandResponse;
import vent.command.repository.RemoteCommandRepository;
import vent.command.rule.BoostCheckInput;
import vent.command.rule.BoostCommandPolicy;
import vent.command.rule.BoostDecision;
import vent.gateway.domain.Gateway;
import vent.gateway.repository.GatewayRepository;
import vent.site.domain.Device;
import vent.site.domain.type.DeviceType;
import vent.site.repository.DeviceRepository;

/**
 * 원격 명령 요청 유스케이스 (스펙 7.1, #8).
 *
 * <p>보내기 전 검사({@link BoostCommandPolicy})를 거쳐, 잘못된 요청이면 기록하지 않고 오류로, 게이트웨이가 연결되지 않았으면
 * REJECTED_OFFLINE으로 기록하고, 그 밖에는 보냄(SENT)으로 기록한다. 발행은 저장이 커밋된 뒤에 한다. 먼저 발행하고 저장이
 * 실패하면 장비가 서버가 모르는 명령을 적용해 버리기 때문이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Service
public class RemoteCommandService {

    private static final Logger log = LoggerFactory.getLogger(RemoteCommandService.class);
    private static final int RECENT_COMMANDS = 5;

    private final DeviceRepository deviceRepository;
    private final GatewayRepository gatewayRepository;
    private final RemoteCommandRepository commandRepository;
    private final BoostCommandPolicy policy;
    private final ApplicationEventPublisher eventPublisher;

    public RemoteCommandService(DeviceRepository deviceRepository, GatewayRepository gatewayRepository,
                                RemoteCommandRepository commandRepository, BoostCommandPolicy policy,
                                ApplicationEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.gatewayRepository = gatewayRepository;
        this.commandRepository = commandRepository;
        this.policy = policy;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 부스트를 요청한다.
     *
     * @param deviceId 대상 환기장치 ID
     * @param request  급기와 지속시간
     * @param now      요청 시각
     * @return 기록한 명령 (보냄 또는 연결이 끊겨 보내지 않음)
     * @throws NoSuchElementException 없는 장비
     * @throws BoostRequestException  잘못된 요청 (환기장치가 아님, 지원 기능 없음, 기준 밖)
     */
    @Transactional
    public RemoteCommandResponse requestBoost(String deviceId, BoostRequest request, OffsetDateTime now) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new NoSuchElementException("없는 장비: " + deviceId));
        if (device.getType() != DeviceType.VENTILATOR) {
            throw new BoostRequestException("부스트는 열회수 환기장치에만 보낼 수 있어요");
        }
        Gateway gateway = gatewayRepository.findById(device.getGatewayId()).orElseThrow();

        BoostDecision decision = policy.decide(new BoostCheckInput(gateway.getConnectionState(), gateway.getCapabilities(),
                device.getModel(), request.supplyCmh(), request.durationSec()));
        return switch (decision.kind()) {
            case INVALID -> throw new BoostRequestException(decision.message());
            case REJECT_OFFLINE -> {
                RemoteCommand command = commandRepository.save(
                        RemoteCommand.rejectedOfflineBoost(deviceId, decision.params(), now));
                log.info("원격 명령 보내지 않음(게이트웨이 연결 안 됨): {} {} ({})", deviceId, command.getId(),
                        gateway.getConnectionState());
                yield RemoteCommandResponse.from(command);
            }
            case SEND -> {
                RemoteCommand command = commandRepository.save(RemoteCommand.sentBoost(deviceId, decision.params(), now));
                eventPublisher.publishEvent(new RemoteCommandIssuedEvent(
                        command.getId(), device.getSite().getId(), deviceId, decision.params(), now));
                yield RemoteCommandResponse.from(command);
            }
        };
    }

    /** 한 장비의 최근 명령 (최근 것부터 5개). */
    @Transactional(readOnly = true)
    public List<RemoteCommandResponse> findRecent(String deviceId) {
        return commandRepository.findByDeviceIdOrderByRequestedAtDesc(deviceId, Limit.of(RECENT_COMMANDS)).stream()
                .map(RemoteCommandResponse::from)
                .toList();
    }
}
