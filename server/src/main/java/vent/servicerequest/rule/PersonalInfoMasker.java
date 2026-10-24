package vent.servicerequest.rule;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 고객 문의 글의 연락처 가리기 (스펙 8.2, #10).
 *
 * <p>고객 이름·연락처는 A/S 신청에 따로 저장하고 AI에 보내지 않는다. 고객이 문의 글에 직접 쓴 전화번호와 이메일도
 * AI에 보내기 전에 {@code [연락처]}로 바꾼다. 완벽한 개인정보 탐지는 아니고, 흔한 형식만 막는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Component
public class PersonalInfoMasker {

    private static final String MASK = "[연락처]";
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    /** 휴대폰(010-1234-5678, 01012345678)과 유선(02-123-4567, 031-123-4567) */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)0\\d{1,2}-?\\d{3,4}-?\\d{4}(?!\\d)");

    public String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = EMAIL.matcher(text).replaceAll(MASK);
        return PHONE.matcher(masked).replaceAll(MASK);
    }
}
