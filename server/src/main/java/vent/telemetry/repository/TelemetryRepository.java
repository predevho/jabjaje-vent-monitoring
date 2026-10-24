package vent.telemetry.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
     * 한 장비의 그 품질 측정값 중 측정 시각이 since 이후인 것을 늦은 것부터 가져온다. 이상 규칙이 OK 측정값만 볼 때 쓴다.
     *
     * @since 2026-10-24 (#5)
     */
    List<Telemetry> findByDeviceIdAndQualityAndMeasuredAtAfterOrderByMeasuredAtDesc(
            String deviceId, TelemetryQuality quality, OffsetDateTime since);

    /**
     * 그 시각과 같거나 그 전의 마지막 측정값. 후드 연동 판정의 "직전 값"이다.
     *
     * @since 2026-10-24 (#7)
     */
    Optional<Telemetry> findFirstByDeviceIdAndQualityAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(
            String deviceId, TelemetryQuality quality, OffsetDateTime at);

    /**
     * 그 시각과 같거나 그 뒤의 첫 측정값. 후드 연동 판정의 "60초 뒤 값"이다.
     *
     * @since 2026-10-24 (#7)
     */
    Optional<Telemetry> findFirstByDeviceIdAndQualityAndMeasuredAtGreaterThanEqualOrderByMeasuredAtAsc(
            String deviceId, TelemetryQuality quality, OffsetDateTime at);
}
