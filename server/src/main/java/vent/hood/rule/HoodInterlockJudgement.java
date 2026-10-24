package vent.hood.rule;

import java.time.OffsetDateTime;
import vent.hood.domain.type.HoodInterlockResult;

/**
 * 후드 연동 확인 결과와 그 근거.
 *
 * @param result          정상, 무반응, 확인 보류
 * @param beforeSupplyCmh HOOD_ON 직전 급기. 없으면 null
 * @param afterSupplyCmh  60초 뒤 첫 급기. 없으면 null
 * @param at              판단 근거가 된 시각(60초 뒤 측정값의 측정 시각, 없으면 확인한 시각)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public record HoodInterlockJudgement(HoodInterlockResult result, Integer beforeSupplyCmh, Integer afterSupplyCmh,
                                     OffsetDateTime at) {

    static HoodInterlockJudgement inconclusive(HoodInterlockReading before, HoodInterlockReading after, OffsetDateTime at) {
        return new HoodInterlockJudgement(HoodInterlockResult.INCONCLUSIVE,
                before == null ? null : before.supplyCmh(), after == null ? null : after.supplyCmh(), at);
    }
}
