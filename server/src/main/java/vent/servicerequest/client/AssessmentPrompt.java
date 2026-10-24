package vent.servicerequest.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 소견 프롬프트 (스펙 8.3, #12).
 *
 * <p>시스템 프롬프트(역할, 출력 규칙, 소견 기준표, 판매 부품 목록)는 버전이 붙은 파일
 * ({@code prompts/assessment-system-v1.md})에서 읽는다. 요청마다 같은 글이라 프롬프트 캐싱이 걸린다. 사용자 메시지에는
 * 요청마다 다른 상황 요약 JSON과 구분 태그로 감싼 고객 문의만 넣는다. 파일 맨 앞의 HTML 주석(작성자 정보)은 보내지 않는다.
 * 상황 요약 JSON은 자체 설정(시각은 ISO 문자열)으로 만들어, 운영과 평가 세트가 글자 하나까지 같은 프롬프트를 보낸다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public class AssessmentPrompt {

    /** 시스템 프롬프트 버전. 프롬프트를 고치면 새 파일을 만들고 올린다. AI 입력 사본과 평가 보고서에 남는다 */
    public static final String VERSION = "v1";

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final String systemTemplate;

    public AssessmentPrompt() {
        this.systemTemplate = load("prompts/assessment-system-" + VERSION + ".md");
    }

    /** 시스템 프롬프트. 부품 목록 자리를 채운다. */
    public String system(List<String> parts) {
        String partList = parts.stream().map(part -> "- " + part).collect(Collectors.joining("\n"));
        return systemTemplate.replace("{{parts}}", partList);
    }

    /** 사용자 메시지: 상황 요약 JSON과 고객 문의. */
    public String user(AssessmentInput input) {
        try {
            return "상황 요약:\n```json\n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(input.summary())
                    + "\n```\n\n고객 문의:\n" + input.customerMessage();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상황 요약을 JSON으로 만들 수 없음", e);
        }
    }

    private static String load(String path) {
        try (InputStream in = AssessmentPrompt.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("프롬프트 파일 없음: " + path);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.replaceFirst("(?s)^\\s*<!--.*?-->\\s*", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
