package vent.global.mqtt;

/**
 * MQTT 메시지 처리 결과.
 *
 * <p>{@link MqttClientManager}가 이 값을 보고 브로커에 수신 확인(PUBACK)을 보낼지 정한다 (ADR-0012).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
public enum MqttHandleResult {
    /**
     * 처리가 끝났다. 저장에 성공했거나, 다시 받아도 소용없는 메시지(형식 오류, 장비 ID 불일치 등)라 버렸다.
     * 수신 확인을 보낸다.
     */
    DONE,
    /**
     * DB 장애처럼 잠깐의 문제로 처리하지 못했다. 수신 확인을 보내지 않고, 잠시 뒤 다시 연결해서 브로커가 다시 보내게 한다.
     */
    RETRY_LATER
}
