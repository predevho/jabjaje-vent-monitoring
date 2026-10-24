package vent.hood.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import vent.hood.domain.type.HoodInterlockResult;

/**
 * 후드 연동을 확인했다는 이벤트.
 *
 * <p>확인 결과를 기록한 트랜잭션 안에서 발행되고, 받는 쪽(VEMC 무반응 이상)도 같은 트랜잭션에서 처리한다(ADR-0014와 같은 방식).
 *
 * @param vemcDeviceId    HOOD_ON을 보낸 VEMC 장비 ID
 * @param result          확인 결과
 * @param beforeSupplyCmh HOOD_ON 직전 급기. 없으면 null
 * @param afterSupplyCmh  기다린 뒤 첫 급기. 없으면 null
 * @param responseWait    HOOD_ON 뒤 기다린 시간(설정값). 이상 설명에 쓴다
 * @param at              판단 근거가 된 시각
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public record HoodInterlockJudgedEvent(String vemcDeviceId, HoodInterlockResult result, Integer beforeSupplyCmh,
                                       Integer afterSupplyCmh, Duration responseWait, OffsetDateTime at) {
}
