package vent.servicerequest.service;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 소견 설정 ({@code vent.assessment.*}, 스펙 8장, #10).
 *
 * @param window            상황 요약 기간 (최근 7일)
 * @param telemetryInterval 측정 주기. 데이터 충분도의 분모(받았어야 할 측정값 수)와 바이패스 시간을 셀 때 쓴다
 * @param maxAttempts       검증 실패 시 재시도를 포함한 시도 수 (스펙 8.5: 1회 재시도 → 2)
 * @param callTimeout       AI 호출 한 번의 최대 대기 시간 (스펙 8.6). AI 구현이 이 시간을 넘기면 실패로 끝내야 한다
 * @param pendingAfter      접수된 채 이만큼 지난 신청은 재처리 스케줄러가 다시 맡는다. 시도를 모두 마칠 시간
 *                          (callTimeout × maxAttempts)보다 길어야 같은 신청을 두 곳이 처리하지 않는다
 * @param parts             판매 중인 교체 부품 이름. AI 입력과 검증에 같이 쓴다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@ConfigurationProperties(prefix = "vent.assessment")
public record AssessmentProperties(@DefaultValue("7d") Duration window,
                                   @DefaultValue("30s") Duration telemetryInterval,
                                   @DefaultValue("2") int maxAttempts,
                                   @DefaultValue("30s") Duration callTimeout,
                                   @DefaultValue("2m") Duration pendingAfter,
                                   @DefaultValue List<String> parts) {

    public AssessmentProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("vent.assessment.max-attempts는 1부터: " + maxAttempts);
        }
        if (pendingAfter.compareTo(callTimeout.multipliedBy(maxAttempts)) <= 0) {
            throw new IllegalArgumentException("vent.assessment.pending-after(" + pendingAfter
                    + ")는 call-timeout × max-attempts(" + callTimeout.multipliedBy(maxAttempts) + ")보다 길어야 함");
        }
        parts = List.copyOf(parts);
    }
}
