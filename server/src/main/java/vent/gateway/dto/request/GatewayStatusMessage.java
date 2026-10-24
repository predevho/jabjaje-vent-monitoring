package vent.gateway.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 게이트웨이 상태 메시지 ({@code jabjaje/{현장ID}/{게이트웨이ID}/status}, 스펙 4.1, 4.4).
 *
 * <p>게이트웨이는 연결할 때 {@code online}과 버전·지원 기능을 retained로 보내고, Last Will로 {@code offline}을 등록한다.
 * 모르는 필드는 무시한다(필드 추가만 허용, 스펙 4.4).
 *
 * @param state           {@code online} 또는 {@code offline}
 * @param firmwareVersion 게이트웨이 버전 (online일 때)
 * @param schemaVersion   메시지 형식 버전
 * @param capabilities    지원 기능 (online일 때). 예: BOOST, HOOD_EVENT
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayStatusMessage(String state, String firmwareVersion, Integer schemaVersion, List<String> capabilities) {

    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";

    public boolean isOnline() {
        return ONLINE.equals(state);
    }

    public boolean isOffline() {
        return OFFLINE.equals(state);
    }
}
