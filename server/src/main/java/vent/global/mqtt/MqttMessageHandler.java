package vent.global.mqtt;

/**
 * MQTT 메시지 처리기.
 *
 * <p>기능마다 이 인터페이스를 구현한 빈을 두면 {@link MqttClientManager}가 연결될 때 구독하고, 맞는 토픽의 메시지를 넘겨준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public interface MqttMessageHandler {

    /** 구독할 토픽 필터. {@code +}, {@code #} 와일드카드를 쓸 수 있다. */
    String topicFilter();

    /**
     * 메시지 하나를 처리한다. Paho 콜백 스레드에서 호출된다.
     *
     * <p>다시 받으면 처리할 수 있는 실패(DB 장애)는 {@link MqttHandleResult#RETRY_LATER}를 돌려준다.
     * 다시 받아도 소용없는 메시지는 로그를 남기고 {@link MqttHandleResult#DONE}을 돌려준다. 그렇지 않으면 같은 메시지를 끝없이 다시 받는다.
     *
     * @param topic   실제로 받은 토픽
     * @param payload 메시지 본문
     * @return 처리 결과. 이 값으로 수신 확인 여부가 정해진다
     */
    MqttHandleResult handle(String topic, byte[] payload);
}
