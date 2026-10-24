package vent.hood.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.hood.domain.type.HoodInterlockResult;

/**
 * 후드 연동 확인 단위 테스트.
 *
 * <p>테스트 지점: 확인 클래스의 공개 메서드. 기준은 #7에서 정한 값(HOOD_ON 직전 값과 60초 뒤 첫 값 비교, 10% 넘게 오르면
 * 정상, 두 값은 각각 2분 안에 잰 것, 24시간 넘게 이후 값이 없으면 확인 보류)이고 시각과 급기는 직접 적은 숫자로 넣는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
class HoodInterlockJudgeTest {

    private static final OffsetDateTime HOOD_ON = OffsetDateTime.of(2026, 10, 24, 6, 0, 0, 0, ZoneOffset.UTC);
    private static final HoodInterlockReading BEFORE = new HoodInterlockReading(HOOD_ON.minusSeconds(20), 200);

    private final HoodInterlockJudge judge = new HoodInterlockJudge(
            new HoodInterlockProperties(Duration.ofSeconds(60), 0.1, Duration.ofMinutes(2), Duration.ofHours(24)));

    @Test
    @DisplayName("60초 뒤 급기가 10% 넘게 오르면 정상이고, 기준 시각은 그 측정값의 측정 시각이다")
    void riseAboveTenPercentIsConfirmed() {
        HoodInterlockReading after = new HoodInterlockReading(HOOD_ON.plusSeconds(70), 221);

        HoodInterlockJudgement judgement = judge(BEFORE, after, null, HOOD_ON.plusMinutes(2)).orElseThrow();

        assertThat(judgement.result()).isEqualTo(HoodInterlockResult.CONFIRMED);
        assertThat(judgement.beforeSupplyCmh()).isEqualTo(200);
        assertThat(judgement.afterSupplyCmh()).isEqualTo(221);
        assertThat(judgement.at()).isEqualTo(HOOD_ON.plusSeconds(70));
    }

    @Test
    @DisplayName("10% 정각이나 그보다 덜 오르면 무반응이다")
    void riseOfTenPercentOrLessIsNoResponse() {
        assertThat(resultOf(BEFORE, new HoodInterlockReading(HOOD_ON.plusSeconds(70), 220), null))
                .isEqualTo(HoodInterlockResult.NO_RESPONSE);
        assertThat(resultOf(BEFORE, new HoodInterlockReading(HOOD_ON.plusSeconds(70), 203), null))
                .isEqualTo(HoodInterlockResult.NO_RESPONSE);
    }

    @Test
    @DisplayName("60초 뒤 측정값이 아직 없으면 확인하지 않고 기다린다. 직전 값이 아직 안 들어왔어도 기다린다")
    void waitsUntilAfterReadingArrives() {
        assertThat(judge(BEFORE, null, null, HOOD_ON.plusMinutes(10))).isEmpty();
        assertThat(judge(null, null, null, HOOD_ON.plusSeconds(30))).isEmpty();
    }

    @Test
    @DisplayName("24시간이 지나도 60초 뒤 측정값이 없으면 확인 보류로 닫는다")
    void givesUpAfterTwentyFourHours() {
        assertThat(judge(BEFORE, null, null, HOOD_ON.plusHours(24).plusSeconds(1)).orElseThrow().result())
                .isEqualTo(HoodInterlockResult.INCONCLUSIVE);
    }

    @Test
    @DisplayName("직전 측정값이 없거나 2분 넘게 전에 잰 값이면 비교할 수 없어 확인 보류")
    void missingOrStaleBeforeIsInconclusive() {
        HoodInterlockReading after = new HoodInterlockReading(HOOD_ON.plusSeconds(70), 280);
        assertThat(resultOf(null, after, null)).isEqualTo(HoodInterlockResult.INCONCLUSIVE);
        assertThat(resultOf(new HoodInterlockReading(HOOD_ON.minusHours(3), 200), after, null))
                .isEqualTo(HoodInterlockResult.INCONCLUSIVE);
    }

    @Test
    @DisplayName("60초 뒤 첫 측정값이 2분 넘게 늦게 잰 값이면 확인 보류: 측정값이 빠진 구간이다")
    void lateAfterReadingIsInconclusive() {
        assertThat(resultOf(BEFORE, new HoodInterlockReading(HOOD_ON.plusHours(2), 201), null))
                .isEqualTo(HoodInterlockResult.INCONCLUSIVE);
    }

    @Test
    @DisplayName("60초 안에 후드를 끄면 확인 보류: 60초 뒤에는 이미 급기가 원래대로다")
    void hoodOffWithinWaitIsInconclusive() {
        assertThat(resultOf(BEFORE, new HoodInterlockReading(HOOD_ON.plusSeconds(70), 201), HOOD_ON.plusSeconds(30)))
                .isEqualTo(HoodInterlockResult.INCONCLUSIVE);
    }

    @Test
    @DisplayName("60초 뒤 첫 측정값이 후드를 끈 뒤에 잰 값이면 확인 보류")
    void afterReadingTakenAfterHoodOffIsInconclusive() {
        assertThat(resultOf(BEFORE, new HoodInterlockReading(HOOD_ON.plusSeconds(110), 201), HOOD_ON.plusSeconds(100)))
                .isEqualTo(HoodInterlockResult.INCONCLUSIVE);
    }

    @Test
    @DisplayName("후드가 이미 켜져 있었으면(HOOD_ON 연속) 확인 보류: 직전 급기가 이미 올라 있다")
    void hoodAlreadyOnIsInconclusive() {
        HoodInterlockInput input = new HoodInterlockInput(HOOD_ON, null, true,
                new HoodInterlockReading(HOOD_ON.minusSeconds(20), 280), new HoodInterlockReading(HOOD_ON.plusSeconds(70), 281));

        assertThat(judge.judge(input, HOOD_ON.plusMinutes(2)).orElseThrow().result()).isEqualTo(HoodInterlockResult.INCONCLUSIVE);
    }

    private HoodInterlockResult resultOf(HoodInterlockReading before, HoodInterlockReading after, OffsetDateTime hoodOffAt) {
        return judge(before, after, hoodOffAt, HOOD_ON.plusHours(3)).orElseThrow().result();
    }

    private Optional<HoodInterlockJudgement> judge(HoodInterlockReading before, HoodInterlockReading after,
                                                   OffsetDateTime hoodOffAt, OffsetDateTime now) {
        return judge.judge(new HoodInterlockInput(HOOD_ON, hoodOffAt, false, before, after), now);
    }
}
