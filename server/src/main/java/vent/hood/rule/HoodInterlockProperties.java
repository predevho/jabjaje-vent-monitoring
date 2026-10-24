package vent.hood.rule;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 후드 연동 확인 기준 ({@code vent.hood.interlock.*}, 스펙 6.1·6.2, #7).
 *
 * <p>검사 주기({@code check-interval})와 검사를 켤지({@code check-enabled})는 스케줄러가 직접 읽는다.
 *
 * @param responseWait       HOOD_ON 뒤 이만큼 지난 첫 측정값으로 확인한다 (기본 60초)
 * @param minSupplyRiseRatio 급기가 직전보다 이 비율 넘게 올라야 정상 (기본 0.1 = 10%). 측정값의 평소 흔들림보다 크게 둔다
 * @param maxReadingGap      직전 값은 HOOD_ON 전 이 시간 안, 이후 값은 HOOD_ON + 60초 뒤 이 시간 안에 잰 것만 쓴다 (기본 2분).
 *                           측정값이 빠진 구간의 먼 값으로 비교하지 않기 위해서다
 * @param giveUpAfter        이 시간이 지나도 확인할 측정값이 없으면 확인 보류로 닫는다 (기본 24시간 = 게이트웨이 버퍼 크기)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@ConfigurationProperties(prefix = "vent.hood.interlock")
public record HoodInterlockProperties(
        @DefaultValue("60s") Duration responseWait,
        @DefaultValue("0.1") double minSupplyRiseRatio,
        @DefaultValue("2m") Duration maxReadingGap,
        @DefaultValue("24h") Duration giveUpAfter) {
}
