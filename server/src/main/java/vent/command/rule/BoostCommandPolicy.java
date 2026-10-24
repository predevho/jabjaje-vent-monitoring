package vent.command.rule;

import org.springframework.stereotype.Component;
import vent.command.domain.BoostParams;
import vent.gateway.domain.type.ConnectionState;

/**
 * 부스트 보내기 전 검사 (스펙 7.1, 4.4, 7.4, #8).
 *
 * <p>아래 순서로 본다. 요청 자체가 잘못됐으면 연결 상태와 상관없이 잘못된 요청이다.
 * <ol>
 *   <li>게이트웨이 지원 기능에 BOOST가 없으면 잘못된 요청 (스펙 4.4)</li>
 *   <li>최대 풍량을 모르는 모델이거나 급기가 0 이하·최대 풍량 초과면 잘못된 요청</li>
 *   <li>지속시간이 0 이하이거나 2시간을 넘으면 잘못된 요청</li>
 *   <li>게이트웨이가 연결됨이 아니면 보내지 않음(REJECTED_OFFLINE)</li>
 *   <li>그 밖에는 보낸다. 배기는 급기와 같은 값으로 함께 올린다 (스펙 7.4)</li>
 * </ol>
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Component
public class BoostCommandPolicy {

    static final String BOOST_CAPABILITY = "BOOST";

    private final BoostCommandProperties properties;

    public BoostCommandPolicy(BoostCommandProperties properties) {
        this.properties = properties;
    }

    public BoostDecision decide(BoostCheckInput input) {
        if (input.capabilities() == null || !input.capabilities().contains(BOOST_CAPABILITY)) {
            return BoostDecision.invalid("이 게이트웨이는 부스트를 지원하지 않아요");
        }
        Integer maxAirflowCmh = properties.modelMaxAirflowCmh() == null ? null
                : properties.modelMaxAirflowCmh().get(input.model());
        if (maxAirflowCmh == null) {
            return BoostDecision.invalid("최대 풍량을 모르는 모델이라 부스트를 보낼 수 없어요: " + input.model());
        }
        if (input.supplyCmh() <= 0 || input.supplyCmh() > maxAirflowCmh) {
            return BoostDecision.invalid("풍량은 1~%dCMH(모델 최대 풍량) 사이여야 해요".formatted(maxAirflowCmh));
        }
        long maxSeconds = properties.maxDuration().toSeconds();
        if (input.durationSec() <= 0 || input.durationSec() > maxSeconds) {
            return BoostDecision.invalid("지속시간은 1~%d초(%d시간) 사이여야 해요".formatted(maxSeconds, properties.maxDuration().toHours()));
        }

        BoostParams params = new BoostParams(input.supplyCmh(), input.supplyCmh(), input.durationSec());
        return input.connectionState() == ConnectionState.CONNECTED
                ? BoostDecision.send(params)
                : BoostDecision.rejectOffline(params);
    }
}
