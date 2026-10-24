package vent.global.mqtt;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionException;

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
    RETRY_LATER;

    /**
     * DB 처리 실패를 결과로 바꾼다. 다시 하면 될 수 있는 것(연결 실패, 일시 오류)만 {@link #RETRY_LATER},
     * 무결성 위반처럼 다시 해도 같은 것은 {@link #DONE}이다. 다시 받으면 같은 메시지가 끝없이 돌아온다(poison message, ADR-0012).
     *
     * @since 2026-10-24 (#6)
     */
    public static MqttHandleResult forFailure(RuntimeException e) {
        boolean transientFailure = e instanceof TransientDataAccessException
                || e instanceof DataAccessResourceFailureException
                || e instanceof TransactionException;
        return transientFailure ? RETRY_LATER : DONE;
    }
}
