package vent.command.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vent.command.domain.BoostParams;
import vent.command.rule.BoostDecision.Kind;
import vent.gateway.domain.type.ConnectionState;

/**
 * 부스트 보내기 전 검사 단위 테스트.
 *
 * <p>테스트 지점: 검사 클래스의 공개 메서드. 기준은 스펙 7.1(풍량은 모델 최대 풍량 이하, 지속시간 2시간 이하, 연결됨이 아니면
 * REJECTED_OFFLINE)과 4.4(지원 기능에 BOOST)이고, 7.4(급기와 배기를 함께 올림)를 확인한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
class BoostCommandPolicyTest {

    private static final List<String> BOOST_CAPABLE = List.of("BOOST", "HOOD_EVENT");

    private final BoostCommandPolicy policy = new BoostCommandPolicy(new BoostCommandProperties(
            Duration.ofHours(2), Map.of("DOMEKT R 300V", 300, "DOMEKT R 450V", 450)));

    @Test
    @DisplayName("연결됨이고 기준 안이면 보내고, 배기는 급기와 같은 값으로 함께 올린다")
    void sendsWithBalancedExhaust() {
        BoostDecision decision = decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 273, 1800);

        assertThat(decision.kind()).isEqualTo(Kind.SEND);
        assertThat(decision.params()).isEqualTo(new BoostParams(273, 273, 1800));
    }

    @Test
    @DisplayName("모델 최대 풍량까지는 되고, 넘으면 잘못된 요청")
    void rejectsAboveModelMaxAirflow() {
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 300, 1800).kind()).isEqualTo(Kind.SEND);
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 301, 1800).kind()).isEqualTo(Kind.INVALID);
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 450V", 450, 1800).kind()).isEqualTo(Kind.SEND);
    }

    @Test
    @DisplayName("지속시간은 2시간까지, 넘거나 0 이하면 잘못된 요청")
    void rejectsDurationOutOfRange() {
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 250, 7200).kind()).isEqualTo(Kind.SEND);
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 250, 7201).kind()).isEqualTo(Kind.INVALID);
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 250, 0).kind()).isEqualTo(Kind.INVALID);
    }

    @Test
    @DisplayName("풍량이 0 이하이거나 최대 풍량을 모르는 모델이면 잘못된 요청")
    void rejectsNonPositiveAirflowAndUnknownModel() {
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "DOMEKT R 300V", 0, 1800).kind()).isEqualTo(Kind.INVALID);
        assertThat(decide(ConnectionState.CONNECTED, BOOST_CAPABLE, "UNKNOWN", 200, 1800).kind()).isEqualTo(Kind.INVALID);
    }

    @Test
    @DisplayName("게이트웨이 지원 기능에 BOOST가 없으면 잘못된 요청 (스펙 4.4)")
    void rejectsWhenGatewayDoesNotSupportBoost() {
        assertThat(decide(ConnectionState.CONNECTED, List.of("HOOD_EVENT"), "DOMEKT R 300V", 250, 1800).kind())
                .isEqualTo(Kind.INVALID);
        assertThat(decide(ConnectionState.CONNECTED, null, "DOMEKT R 300V", 250, 1800).kind()).isEqualTo(Kind.INVALID);
    }

    @Test
    @DisplayName("요청은 맞지만 게이트웨이가 연결됨이 아니면 보내지 않고 REJECTED_OFFLINE")
    void notConnectedIsRejectedOffline() {
        for (ConnectionState state : List.of(ConnectionState.PENDING, ConnectionState.INTERRUPTED, ConnectionState.OFFLINE)) {
            assertThat(decide(state, BOOST_CAPABLE, "DOMEKT R 300V", 250, 1800).kind()).isEqualTo(Kind.REJECT_OFFLINE);
        }
    }

    private BoostDecision decide(ConnectionState state, List<String> capabilities, String model, int supplyCmh, int durationSec) {
        return policy.decide(new BoostCheckInput(state, capabilities, model, supplyCmh, durationSec));
    }
}
