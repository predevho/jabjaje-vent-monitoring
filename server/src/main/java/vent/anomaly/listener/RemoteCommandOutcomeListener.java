package vent.anomaly.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vent.anomaly.service.CommandTimeoutAnomalyService;
import vent.command.service.RemoteCommandAnsweredEvent;
import vent.command.service.RemoteCommandTimedOutEvent;

/**
 * 원격 명령 시간 초과·응답 이벤트 수신 처리기 (#9).
 *
 * <p>명령 상태를 바꾼 트랜잭션 안에서 바로 처리한다({@code @EventListener}). 시간 초과 상태와 COMMAND_TIMEOUT 이상은
 * 함께 저장되거나 함께 취소되어야 하기 때문이다(ADR-0014와 같은 방식).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#9)
 */
@Component
public class RemoteCommandOutcomeListener {

    private final CommandTimeoutAnomalyService commandTimeoutAnomalyService;

    public RemoteCommandOutcomeListener(CommandTimeoutAnomalyService commandTimeoutAnomalyService) {
        this.commandTimeoutAnomalyService = commandTimeoutAnomalyService;
    }

    @EventListener
    public void onTimedOut(RemoteCommandTimedOutEvent event) {
        commandTimeoutAnomalyService.onTimedOut(event);
    }

    @EventListener
    public void onAnswered(RemoteCommandAnsweredEvent event) {
        commandTimeoutAnomalyService.onAnswered(event);
    }
}
