package vent.command.dto.request;

/**
 * 부스트 요청 본문 ({@code POST /api/devices/{장비ID}/boost}, #8).
 *
 * <p>배기는 받지 않는다. 서버가 급기와 같은 값으로 채워 함께 올린다 (스펙 7.4).
 *
 * @param supplyCmh   부스트 동안의 급기 (CMH). 모델 최대 풍량 이하
 * @param durationSec 지속시간 (초). 2시간 이하
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record BoostRequest(int supplyCmh, int durationSec) {
}
