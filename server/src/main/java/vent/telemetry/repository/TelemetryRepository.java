package vent.telemetry.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vent.telemetry.domain.Telemetry;
import vent.telemetry.domain.type.TelemetryQuality;

/**
 * 측정값 저장소.
 *
 * <p>조회는 JPA로 하고, 중복을 무시하는 저장은 {@link TelemetryInsertRepository}의 직접 SQL로 한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public interface TelemetryRepository extends JpaRepository<Telemetry, Long>, TelemetryInsertRepository {

    /**
     * 장비마다 품질이 OK인 측정값 중 측정 시각이 가장 늦은 것 하나씩 가져온다 (PostgreSQL DISTINCT ON).
     * 시계가 틀린 장비의 미래 시각 측정값이 계속 최신값으로 남지 않도록 OK만 본다 (스펙 9.1 C16).
     */
    @Query(value = """
            SELECT DISTINCT ON (device_id) *
            FROM telemetry
            WHERE quality = 'OK'
            ORDER BY device_id, measured_at DESC
            """, nativeQuery = true)
    List<Telemetry> findLatestPerDevice();

    /** 한 장비의 측정값을 측정 시각이 늦은 것부터 가져온다. */
    List<Telemetry> findByDeviceIdOrderByMeasuredAtDesc(String deviceId, Limit limit);

    /**
     * 한 장비의 측정값 중 그 품질인 것만 측정 시각이 늦은 것부터 가져온다. 이상 규칙이 OK 측정값만 볼 때 쓴다.
     *
     * @since 2026-10-24 (#4)
     */
    List<Telemetry> findByDeviceIdAndQualityOrderByMeasuredAtDesc(String deviceId, TelemetryQuality quality, Limit limit);
}
