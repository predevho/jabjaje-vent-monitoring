package vent.telemetry.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vent.telemetry.domain.Telemetry;

/**
 * 측정값 저장소.
 *
 * <p>조회는 JPA로 하고, 중복을 무시하는 저장은 {@link TelemetryInsertRepository}의 직접 SQL로 한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public interface TelemetryRepository extends JpaRepository<Telemetry, Long>, TelemetryInsertRepository {

    /** 장비마다 측정 시각이 가장 늦은 측정값 하나씩 가져온다 (PostgreSQL DISTINCT ON). */
    @Query(value = """
            SELECT DISTINCT ON (device_id) *
            FROM telemetry
            ORDER BY device_id, measured_at DESC
            """, nativeQuery = true)
    List<Telemetry> findLatestPerDevice();

    /** 한 장비의 측정값을 측정 시각이 늦은 것부터 가져온다. */
    List<Telemetry> findByDeviceIdOrderByMeasuredAtDesc(String deviceId, Limit limit);
}
