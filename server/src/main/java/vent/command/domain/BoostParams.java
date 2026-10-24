package vent.command.domain;

/**
 * 부스트 파라미터 (스펙 4.2 원격 명령의 {@code params}).
 *
 * @param supplyCmh   부스트 동안의 급기 (CMH)
 * @param exhaustCmh  부스트 동안의 배기 (CMH). 급기와 함께 올려 실내 압력 균형을 지킨다 (스펙 7.4)
 * @param durationSec 지속시간 (초). 지나면 장비가 스스로 원래대로 돌아간다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record BoostParams(int supplyCmh, int exhaustCmh, int durationSec) {
}
