package vent.anomaly.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vent.anomaly.service.VemcNoResponseAnomalyService;
import vent.hood.service.HoodInterlockJudgedEvent;

/**
 * 후드 연동 확인 이벤트 수신 처리기.
 *
 * <p>확인 결과를 기록한 트랜잭션 안에서 바로 처리한다({@code @EventListener}). 확인 결과와 VEMC 무반응 이상은 함께
 * 저장되거나 함께 취소되어야 하기 때문이다(ADR-0014와 같은 방식). 예외가 나면 확인도 롤백되고 다음 검사에서 다시 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Component
public class HoodInterlockJudgedListener {

    private final VemcNoResponseAnomalyService vemcNoResponseAnomalyService;

    public HoodInterlockJudgedListener(VemcNoResponseAnomalyService vemcNoResponseAnomalyService) {
        this.vemcNoResponseAnomalyService = vemcNoResponseAnomalyService;
    }

    @EventListener
    public void onJudged(HoodInterlockJudgedEvent event) {
        vemcNoResponseAnomalyService.onJudged(event);
    }
}
