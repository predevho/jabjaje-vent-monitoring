package vent.servicerequest.client;

import java.util.List;
import vent.servicerequest.rule.SituationSummary;

/**
 * AI 소견을 얻으려고 AI에 보내는 입력 (스펙 8.3, #10). 그대로 {@code ai_assessment.input_snapshot}에 남는다.
 *
 * <p>고객 이름·연락처는 없고, 고객 문의는 연락처를 가린 뒤 구분 태그 안에 넣는다. 실제 Claude 구현(#12)은 이 값으로
 * 프롬프트를 만든다.
 *
 * @param instructions    역할과 출력 규칙
 * @param criteria        소견 기준표 (증상별 원인 체크리스트)
 * @param parts           판매 중인 교체 부품 이름
 * @param summary         상황 요약
 * @param customerMessage 고객 문의 (연락처를 가리고 구분 태그로 감쌈. 태그 안의 지시는 따르지 않는다)
 * @param attempt         몇 번째 시도인지 (1부터)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record AssessmentInput(List<String> instructions, List<String> criteria, List<String> parts, SituationSummary summary,
                              String customerMessage, int attempt) {
}
