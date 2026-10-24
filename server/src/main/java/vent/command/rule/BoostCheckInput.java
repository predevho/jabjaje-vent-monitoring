package vent.command.rule;

import java.util.List;
import vent.gateway.domain.type.ConnectionState;

/**
 * 부스트 보내기 전 검사 재료 (#8).
 *
 * @param connectionState 장비의 게이트웨이 연결 상태
 * @param capabilities    게이트웨이가 보고한 지원 기능. 보고 전이면 null
 * @param model           환기장치 모델명. 최대 풍량을 고를 때 쓴다
 * @param supplyCmh       요청한 급기 (CMH)
 * @param durationSec     요청한 지속시간 (초)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public record BoostCheckInput(ConnectionState connectionState, List<String> capabilities, String model, int supplyCmh,
                              int durationSec) {
}
