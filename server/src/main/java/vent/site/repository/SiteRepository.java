package vent.site.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vent.site.domain.Site;

/**
 * 현장 저장소.
 *
 * <p>현장을 조회할 때 게이트웨이와 장비를 한 쿼리로 함께 가져와 N+1 조회를 피한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
public interface SiteRepository extends JpaRepository<Site, Long> {

    /** 모든 현장을 게이트웨이, 장비와 함께 id 순서로 가져온다. */
    @EntityGraph(attributePaths = {"gateway", "devices"})
    @Query("select s from Site s order by s.id")
    List<Site> findAllWithGatewayAndDevices();
}
