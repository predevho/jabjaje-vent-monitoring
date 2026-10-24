package vent.hood.repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vent.hood.domain.DeviceEvent;
import vent.hood.domain.type.DeviceEventType;

/**
 * 장비 이벤트 저장소.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long>, DeviceEventInsertRepository {

    /** 아직 확인하지 않은 HOOD_ON의 ID. 일어난 순서대로. 잠그지 않고 읽고, 하나씩 잠가서 확인한다. */
    @Query("""
            select e.id from DeviceEvent e
            where e.type = vent.hood.domain.type.DeviceEventType.HOOD_ON and e.interlockResult is null
            order by e.occurredAt
            """)
    List<Long> findPendingHoodOnIds();

    /** 확인하려고 이벤트 하나를 잠가서 읽는다. 서버가 여러 대여도 같은 이벤트를 두 번 확인하지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DeviceEvent e where e.id = :id")
    Optional<DeviceEvent> findByIdForUpdate(@Param("id") Long id);

    /** 그 장비에서 그 시각 바로 전에 일어난 이벤트. 직전이 HOOD_ON이면 후드가 이미 켜져 있던 것이다. */
    Optional<DeviceEvent> findFirstByDeviceIdAndOccurredAtBeforeOrderByOccurredAtDesc(String deviceId, OffsetDateTime before);

    /** 그 장비에서 그 시각 뒤에 처음 일어난 그 종류의 이벤트. HOOD_ON 뒤 처음 온 HOOD_OFF를 찾는다. */
    Optional<DeviceEvent> findFirstByDeviceIdAndTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            String deviceId, DeviceEventType type, OffsetDateTime after);
}
