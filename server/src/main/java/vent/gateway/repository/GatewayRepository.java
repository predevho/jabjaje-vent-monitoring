package vent.gateway.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vent.gateway.domain.Gateway;
import vent.gateway.domain.type.ConnectionState;

/**
 * 게이트웨이 저장소.
 *
 * <p>연결 상태를 바꾸는 조회는 행 잠금({@code SELECT ... FOR UPDATE})으로 읽는다. 측정값 수신(MQTT 스레드)과
 * 무수신 검사(스케줄러 스레드)가 같은 게이트웨이를 동시에 바꿀 때 한쪽이 다른 쪽을 덮어쓰지 않게 하기 위해서다(#6).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#6)
 */
public interface GatewayRepository extends JpaRepository<Gateway, String> {

    /** 상태를 바꾸려고 게이트웨이 하나를 잠가서 읽는다. 트랜잭션이 끝날 때까지 다른 쪽은 기다린다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gateway g where g.id = :id")
    Optional<Gateway> findByIdForUpdate(@Param("id") String id);

    /** 무수신 검사 대상(연결됨, 일시 끊김)의 ID. 잠그지 않고 읽고, 하나씩 잠가서 다시 확인한다. */
    @Query("select g.id from Gateway g where g.connectionState in :states order by g.id")
    List<String> findIdsByConnectionStateIn(@Param("states") Collection<ConnectionState> states);

    /** 이상 목록에 현장 이름을 붙이려고 게이트웨이와 현장을 함께 읽는다. */
    @EntityGraph(attributePaths = "site")
    List<Gateway> findAllWithSiteByIdIn(Collection<String> ids);
}
