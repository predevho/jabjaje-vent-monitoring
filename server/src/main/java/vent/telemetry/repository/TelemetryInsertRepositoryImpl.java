package vent.telemetry.repository;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 측정값 저장 기능 구현.
 *
 * <p>JPA에는 "충돌이면 무시"가 없어 PostgreSQL의 {@code ON CONFLICT DO NOTHING}을 직접 SQL로 쓴다.
 * 이름 붙은 파라미터는 {@link TelemetryInsertParams}의 구성 요소 이름에서 채운다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
class TelemetryInsertRepositoryImpl implements TelemetryInsertRepository {

    private final JdbcClient jdbcClient;

    TelemetryInsertRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public int insertIgnoringDuplicate(TelemetryInsertParams telemetry) {
        return jdbcClient.sql("""
                        INSERT INTO telemetry (device_id, message_id, measured_at, received_at,
                                               co2_ppm, temp_c, humidity_pct, supply_cmh, exhaust_cmh, filter_pa, bypass)
                        VALUES (:deviceId, :messageId, :measuredAt, :receivedAt,
                                :co2Ppm, :tempC, :humidityPct, :supplyCmh, :exhaustCmh, :filterPa, :bypass)
                        ON CONFLICT (message_id) DO NOTHING
                        """)
                .paramSource(telemetry)
                .update();
    }
}
