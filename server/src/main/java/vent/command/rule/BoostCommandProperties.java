package vent.command.rule;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 부스트 요청 기준 ({@code vent.command.boost.*}, 스펙 7.1, #8).
 *
 * <p>실제 최대 풍량은 제조사 매뉴얼로 확인해야 한다.
 *
 * @param maxDuration          지속시간 상한 (기본 2시간)
 * @param modelMaxAirflowCmh   모델명 → 최대 풍량 (CMH). 없는 모델은 부스트를 보내지 않는다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@ConfigurationProperties(prefix = "vent.command.boost")
public record BoostCommandProperties(@DefaultValue("2h") Duration maxDuration, Map<String, Integer> modelMaxAirflowCmh) {
}
