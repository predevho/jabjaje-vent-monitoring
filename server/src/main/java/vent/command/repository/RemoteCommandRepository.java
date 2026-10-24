package vent.command.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import vent.command.domain.RemoteCommand;

/**
 * 원격 명령 저장소.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
public interface RemoteCommandRepository extends JpaRepository<RemoteCommand, UUID> {

    /** 한 장비의 최근 명령. 요청한 순서의 반대로. */
    List<RemoteCommand> findByDeviceIdOrderByRequestedAtDesc(String deviceId, Limit limit);
}
