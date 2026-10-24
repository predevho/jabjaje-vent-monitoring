package vent.command.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vent.command.domain.RemoteCommand;
import vent.command.domain.type.RemoteCommandStatus;

/**
 * 원격 명령 저장소.
 *
 * <p>상태를 바꾸는 조회는 행 잠금으로 읽는다(#9). 응답 수신(MQTT 스레드), 재시도 검사(스케줄러), 새 요청(HTTP)이 같은 명령을
 * 동시에 바꿀 수 있어서다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public interface RemoteCommandRepository extends JpaRepository<RemoteCommand, UUID> {

    /** 한 장비의 최근 명령. 요청한 순서의 반대로. */
    List<RemoteCommand> findByDeviceIdOrderByRequestedAtDesc(String deviceId, Limit limit);

    /**
     * 응답을 받으려고 명령 하나를 잠가서 읽는다.
     *
     * @since 2026-10-24 (#9)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RemoteCommand c where c.id = :id")
    Optional<RemoteCommand> findByIdForUpdate(@Param("id") UUID id);

    /**
     * 그 장비에서 응답을 기다리는 명령을 잠가서 읽는다. 새 명령이 올 때 기다리던 명령을 밀어내는 데 쓴다.
     *
     * @since 2026-10-24 (#9)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RemoteCommand c where c.deviceId = :deviceId "
            + "and c.status = vent.command.domain.type.RemoteCommandStatus.SENT")
    List<RemoteCommand> findPendingByDeviceIdForUpdate(@Param("deviceId") String deviceId);

    /**
     * 그 상태인 명령의 ID. 잠그지 않고 읽고, 하나씩 잠가서 다시 확인한다.
     *
     * @since 2026-10-24 (#9)
     */
    @Query("select c.id from RemoteCommand c where c.status = :status order by c.sentAt")
    List<UUID> findIdsByStatus(@Param("status") RemoteCommandStatus status);
}
