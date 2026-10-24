package vent.hood.repository;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 장비 이벤트 저장 기능 구현.
 *
 * <p>JPA에는 "충돌이면 무시"가 없어 PostgreSQL의 {@code ON CONFLICT DO NOTHING}을 직접 SQL로 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
class DeviceEventInsertRepositoryImpl implements DeviceEventInsertRepository {

    private final JdbcClient jdbcClient;

    DeviceEventInsertRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public int insertIgnoringDuplicate(DeviceEventInsertParams event) {
        return jdbcClient.sql("""
                        INSERT INTO device_event (device_id, message_id, type, occurred_at, received_at)
                        VALUES (:deviceId, :messageId, :type, :occurredAt, :receivedAt)
                        ON CONFLICT (message_id) DO NOTHING
                        """)
                .paramSource(event)
                .update();
    }
}
