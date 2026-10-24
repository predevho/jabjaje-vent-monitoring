package vent.anomaly.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vent.anomaly.domain.Anomaly;
import vent.anomaly.domain.type.AnomalyRuleName;
import vent.anomaly.domain.type.AnomalyStatus;

/**
 * 이상 저장소.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    /** 그 장비, 그 규칙으로 열린 이상. DB 부분 유니크 인덱스 때문에 많아야 하나다. */
    Optional<Anomaly> findByDeviceIdAndRuleAndStatus(String deviceId, AnomalyRuleName rule, AnomalyStatus status);

    /** 상태별 이상 목록. 최근에 생긴 것부터. */
    List<Anomaly> findByStatusOrderByOpenedAtDesc(AnomalyStatus status);
}
