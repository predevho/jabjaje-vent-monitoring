package vent.servicerequest.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 개인정보 가리기 단위 테스트.
 *
 * <p>테스트 지점: 가리기 클래스의 공개 메서드. 스펙 8.2(고객 이름, 주소, 연락처는 AI에 보내지 않는다)에 따라
 * 고객이 문의 글에 직접 쓴 전화번호와 이메일을 가린다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
class PersonalInfoMaskerTest {

    private final PersonalInfoMasker masker = new PersonalInfoMasker();

    @Test
    @DisplayName("휴대폰·유선 전화번호와 이메일을 [연락처]로 가린다")
    void masksPhoneNumbersAndEmails() {
        String masked = masker.mask("환기가 약해요. 010-1234-5678이나 01098765432, 031-123-4567, kim@example.com으로 연락 주세요");

        assertThat(masked).isEqualTo("환기가 약해요. [연락처]이나 [연락처], [연락처], [연락처]으로 연락 주세요");
    }

    @Test
    @DisplayName("연락처가 없는 문장은 그대로 둔다 (풍량 숫자는 가리지 않는다)")
    void leavesOrdinaryTextAlone() {
        assertThat(masker.mask("급기가 150CMH 정도로 약하고 2024년에 설치했어요")).isEqualTo("급기가 150CMH 정도로 약하고 2024년에 설치했어요");
    }
}
