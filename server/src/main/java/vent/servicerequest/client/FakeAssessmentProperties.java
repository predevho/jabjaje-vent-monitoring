package vent.servicerequest.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 가짜 AI 설정 ({@code vent.assessment.fake.*}, #10).
 *
 * @param mode 응답 방식. 잘못 적으면 시작할 때 오류가 난다
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@ConfigurationProperties(prefix = "vent.assessment.fake")
public record FakeAssessmentProperties(@DefaultValue("normal") FakeAssessmentMode mode) {
}
