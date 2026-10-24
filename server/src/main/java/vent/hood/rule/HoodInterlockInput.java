package vent.hood.rule;

import java.time.OffsetDateTime;

/**
 * 후드 연동 확인에 넘기는 재료 (#7).
 *
 * @param hoodOnAt      HOOD_ON 시각
 * @param hoodOffAt     그 뒤 처음 온 HOOD_OFF 시각. 없으면 null
 * @param hoodAlreadyOn 이 HOOD_ON 직전 이벤트도 HOOD_ON이었는지(사이에 HOOD_OFF 없음). 그러면 직전 급기가 이미 올라 있다
 * @param before        HOOD_ON 시각과 같거나 그 전의 마지막 급기. 없으면 null
 * @param after         HOOD_ON + 60초와 같거나 그 뒤의 첫 급기. 없으면 null
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public record HoodInterlockInput(OffsetDateTime hoodOnAt, OffsetDateTime hoodOffAt, boolean hoodAlreadyOn,
                                 HoodInterlockReading before, HoodInterlockReading after) {
}
