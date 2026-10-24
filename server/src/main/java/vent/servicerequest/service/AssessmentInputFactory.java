package vent.servicerequest.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vent.servicerequest.client.AssessmentInput;
import vent.servicerequest.client.AssessmentPrompt;
import vent.servicerequest.rule.PersonalInfoMasker;
import vent.servicerequest.rule.SituationSummary;

/**
 * AI 입력 만들기 (스펙 8.3, #10).
 *
 * <p>프롬프트 버전, 부품 목록, 상황 요약, 고객 문의를 한데 묶는다. 역할·출력 규칙·소견 기준표는 시스템 프롬프트 파일에
 * 있다({@link AssessmentPrompt}, #12). 고객 문의는 연락처를 가린 뒤 {@code <customer_message>} 태그로 감싸고, 그 안의
 * 지시는 따르지 말라고 시스템 프롬프트에 적는다(프롬프트 인젝션 대응). 고객이 글에 같은 태그를 써서 태그 밖으로 빠져나오지
 * 못하게 문의 안의 태그는 지운다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Component
public class AssessmentInputFactory {

    private static final Pattern TAG = Pattern.compile("(?i)<\\s*/?\\s*customer_message[^>]*>");

    private final PersonalInfoMasker masker;
    private final AssessmentProperties properties;

    public AssessmentInputFactory(PersonalInfoMasker masker, AssessmentProperties properties) {
        this.masker = masker;
        this.properties = properties;
    }

    public AssessmentInput create(SituationSummary summary, String customerMessage, int attempt) {
        String body = TAG.matcher(masker.mask(customerMessage)).replaceAll("[태그 제거]");
        String tagged = "<customer_message>\n" + body + "\n</customer_message>";
        return new AssessmentInput(AssessmentPrompt.VERSION, properties.parts(), summary, tagged, attempt);
    }
}
