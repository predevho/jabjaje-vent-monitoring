package vent.hood.repository;

/**
 * 장비 이벤트 저장 기능 (Spring Data 사용자 정의 조각).
 *
 * <p>QoS 1로 같은 이벤트가 두 번 올 수 있으므로 messageId 충돌을 무시하고 저장한다(스펙 4.3). 측정값 저장과 같은 방식이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public interface DeviceEventInsertRepository {

    /**
     * 이벤트를 저장한다. 같은 messageId가 이미 있으면 아무것도 하지 않는다.
     *
     * @return 새로 저장했으면 1, 중복이라 무시했으면 0
     */
    int insertIgnoringDuplicate(DeviceEventInsertParams event);
}
