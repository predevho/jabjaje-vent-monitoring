package vent.anomaly.rule;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 필터 막힘 규칙 기준 ({@code vent.anomaly.filter-clogged.*}).
 *
 * <p>실제 기준값은 제조사 매뉴얼로 확인해야 한다(스펙 6.1).
 *
 * @param defaultThresholdPa 모델별 기준이 없을 때 쓰는 발생 기준 (Pa)
 * @param resolveMarginPa    해제 기준 = 발생 기준 − 이 값. 기준 근처에서 켜졌다 꺼졌다 반복하는 것을 막는다
 * @param modelThresholdsPa  모델명 → 발생 기준 (Pa)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
@ConfigurationProperties(prefix = "vent.anomaly.filter-clogged")
public record FilterCloggedProperties(int defaultThresholdPa, int resolveMarginPa, Map<String, Integer> modelThresholdsPa) {

    /** 그 모델의 발생 기준. 없으면 기본값. */
    public int thresholdFor(String model) {
        return modelThresholdsPa == null ? defaultThresholdPa : modelThresholdsPa.getOrDefault(model, defaultThresholdPa);
    }
}
