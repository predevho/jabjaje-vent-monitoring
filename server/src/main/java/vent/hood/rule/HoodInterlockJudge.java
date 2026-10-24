package vent.hood.rule;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import vent.hood.domain.type.HoodInterlockResult;

/**
 * 후드 연동 확인 (스펙 6.2, #7).
 *
 * <p>HOOD_ON 직전 급기와, HOOD_ON + 60초 뒤 첫 급기를 비교한다. 측정 시각 기준이고 늦은 측정값도 쓴다(ADR-0003).
 * 끊긴 동안의 후드 연동도 다시 연결된 뒤 확인할 수 있게 하기 위해서다. 아래 순서로 본다.
 * <ol>
 *   <li>후드가 이미 켜져 있었으면(HOOD_ON 연속) 확인 보류. 직전 급기가 이미 올라 있다</li>
 *   <li>60초 안에 후드가 꺼졌으면 확인 보류. 60초 뒤에는 이미 급기가 원래대로다</li>
 *   <li>60초 뒤 측정값이 아직 없으면 기다린다(빈 값). 24시간이 지나면 확인 보류</li>
 *   <li>60초 뒤 측정값을 후드가 꺼진 뒤에 쟀거나, 너무 늦게 잰 값이면 확인 보류</li>
 *   <li>직전 측정값이 없거나 너무 오래된 값이면 확인 보류</li>
 *   <li>급기가 직전보다 10% 넘게 오르면 정상, 아니면 무반응</li>
 * </ol>
 * 60초 뒤 값이 있으면 직전 값도 이미 들어와 있으므로(게이트웨이는 측정값을 순서대로 보낸다), 직전 값 검사는 기다림 뒤에 한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Component
public class HoodInterlockJudge {

    private final HoodInterlockProperties properties;

    public HoodInterlockJudge(HoodInterlockProperties properties) {
        this.properties = properties;
    }

    /**
     * HOOD_ON 이벤트 하나를 확인한다.
     *
     * @param input 확인 재료
     * @param now   확인하는 시각
     * @return 확인 결과. 아직 확인할 수 없으면(측정값을 기다리는 중) 빈 값
     */
    public Optional<HoodInterlockJudgement> judge(HoodInterlockInput input, OffsetDateTime now) {
        HoodInterlockReading before = input.before();
        HoodInterlockReading after = input.after();
        OffsetDateTime checkFrom = input.hoodOnAt().plus(properties.responseWait());

        if (input.hoodAlreadyOn()) {
            return inconclusive(input, input.hoodOnAt());
        }
        if (input.hoodOffAt() != null && input.hoodOffAt().isBefore(checkFrom)) {
            return inconclusive(input, input.hoodOffAt());
        }
        if (after == null) {
            boolean gaveUp = now.isAfter(input.hoodOnAt().plus(properties.giveUpAfter()));
            return gaveUp ? inconclusive(input, now) : Optional.empty();
        }
        boolean afterHoodOff = input.hoodOffAt() != null && !after.measuredAt().isBefore(input.hoodOffAt());
        boolean afterTooLate = after.measuredAt().isAfter(checkFrom.plus(properties.maxReadingGap()));
        if (afterHoodOff || afterTooLate) {
            return inconclusive(input, after.measuredAt());
        }
        if (before == null || before.measuredAt().isBefore(input.hoodOnAt().minus(properties.maxReadingGap()))) {
            return inconclusive(input, after.measuredAt());
        }

        boolean rose = after.supplyCmh() > before.supplyCmh() * (1 + properties.minSupplyRiseRatio());
        HoodInterlockResult result = rose ? HoodInterlockResult.CONFIRMED : HoodInterlockResult.NO_RESPONSE;
        return Optional.of(new HoodInterlockJudgement(result, before.supplyCmh(), after.supplyCmh(), after.measuredAt()));
    }

    private static Optional<HoodInterlockJudgement> inconclusive(HoodInterlockInput input, OffsetDateTime at) {
        return Optional.of(HoodInterlockJudgement.inconclusive(input.before(), input.after(), at));
    }
}
