package vent.servicerequest.repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vent.servicerequest.rule.SituationAnomaly;
import vent.servicerequest.rule.SituationHoodStats;
import vent.servicerequest.rule.SituationTelemetryStats;

/**
 * 상황 요약용 집계 조회 (#10).
 *
 * <p>측정값·후드 연동 확인·이상을 기간으로 집계한다. 원본 행을 가져오지 않고 DB에서 평균·개수만 계산해 온다(7일이면 장비당
 * 측정값 2만 개). 여러 기능의 테이블을 읽기만 하고, PostgreSQL의 {@code FILTER} 집계를 쓰므로 엔티티 대신 SQL로 둔다.
 * SQL 안의 문자열(OK, HOOD_ON, CONFIRMED, NO_RESPONSE, GATEWAY_OFFLINE, OPEN)은 각 기능 enum의 이름이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
@Repository
public class SituationStatsRepository {

    private static final int ANOMALY_LIMIT = 20;

    private final JdbcClient jdbcClient;

    public SituationStatsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 측정값 통계 (품질 OK, 늦은 측정값 포함).
     *
     * @param deviceId    환기장치 ID
     * @param since       기간 시작
     * @param until       기간 끝
     * @param firstPeriod 필터 차압 추세의 시작값을 평균 낼 첫 구간 길이 (7일이면 1일)
     */
    public SituationTelemetryStats telemetry(String deviceId, OffsetDateTime since, OffsetDateTime until, Duration firstPeriod) {
        return jdbcClient.sql("""
                        WITH ok AS (
                            SELECT * FROM telemetry
                            WHERE device_id = :deviceId AND quality = 'OK' AND measured_at >= :since AND measured_at <= :until
                        ), first_period AS (
                            SELECT avg(filter_pa) AS filter_start_avg FROM ok
                            WHERE measured_at < (SELECT min(measured_at) FROM ok) + make_interval(secs => :firstPeriodSec)
                        )
                        SELECT count(*)                                                          AS count,
                               avg(co2_ppm)::float8                                              AS co2_avg,
                               max(co2_ppm)                                                      AS co2_max,
                               (avg(supply_cmh) FILTER (WHERE NOT bypass AND NOT boost))::float8 AS supply_avg,
                               count(*) FILTER (WHERE bypass)                                    AS bypass_count,
                               (SELECT filter_start_avg FROM first_period)::float8               AS filter_start_avg,
                               (SELECT filter_pa FROM ok ORDER BY measured_at DESC LIMIT 1)      AS filter_latest
                        FROM ok
                        """)
                .param("deviceId", deviceId)
                .param("since", since)
                .param("until", until)
                .param("firstPeriodSec", firstPeriod.toSeconds())
                .query(SituationTelemetryStats.class)
                .single();
    }

    /** 그 현장 VEMC의 후드 연동 확인 결과 수 (정상, 무반응). */
    public SituationHoodStats hoodInterlock(long siteId, OffsetDateTime since) {
        return jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE e.interlock_result = 'CONFIRMED')   AS confirmed,
                               count(*) FILTER (WHERE e.interlock_result = 'NO_RESPONSE') AS no_response
                        FROM device_event e JOIN device d ON d.id = e.device_id
                        WHERE d.site_id = :siteId AND e.type = 'HOOD_ON' AND e.occurred_at >= :since
                        """)
                .param("siteId", siteId)
                .param("since", since)
                .query(SituationHoodStats.class)
                .single();
    }

    /** 그 현장 게이트웨이의 오프라인 이상 수. 지금 열린 것과 기간 안에 생긴 것. */
    public long offlineCount(long siteId, OffsetDateTime since) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM anomaly a JOIN gateway g ON g.id = a.gateway_id
                        WHERE g.site_id = :siteId AND a.rule = 'GATEWAY_OFFLINE' AND (a.status = 'OPEN' OR a.opened_at >= :since)
                        """)
                .param("siteId", siteId)
                .param("since", since)
                .query(Long.class)
                .single();
    }

    /** 그 현장(장비·게이트웨이)의 지금 열린 이상. 최근 것부터 20개. */
    public List<SituationAnomaly> openAnomalies(long siteId) {
        return anomalies(siteId, "a.status = 'OPEN'", null);
    }

    /** 그 현장의 기간 안에 생겼다가 닫힌 이상. 최근 것부터 20개. */
    public List<SituationAnomaly> closedAnomalies(long siteId, OffsetDateTime since) {
        return anomalies(siteId, "a.status <> 'OPEN' AND a.opened_at >= :since", since);
    }

    private List<SituationAnomaly> anomalies(long siteId, String condition, OffsetDateTime since) {
        var spec = jdbcClient.sql("""
                        SELECT a.rule, a.severity, a.status, a.opened_at
                        FROM anomaly a
                        LEFT JOIN device d ON d.id = a.device_id
                        LEFT JOIN gateway g ON g.id = a.gateway_id
                        WHERE COALESCE(d.site_id, g.site_id) = :siteId AND %s
                        ORDER BY a.opened_at DESC
                        LIMIT :limit
                        """.formatted(condition))
                .param("siteId", siteId)
                .param("limit", ANOMALY_LIMIT);
        if (since != null) {
            spec = spec.param("since", since);
        }
        return spec.query(SituationAnomaly.class).list();
    }
}
