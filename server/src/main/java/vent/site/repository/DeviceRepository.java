package vent.site.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vent.site.domain.Device;
import vent.site.domain.type.DeviceType;

/**
 * 장비 저장소.
 *
 * <p>측정값을 받을 때 장비가 등록되어 있는지, 어느 현장 장비인지 확인하는 데 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public interface DeviceRepository extends JpaRepository<Device, String> {

    /**
     * 장비들을 현장과 함께 한 쿼리로 가져온다. 이상 목록에 현장 이름을 붙일 때 쓴다.
     *
     * @since 2026-10-24 (#4)
     */
    @EntityGraph(attributePaths = "site")
    List<Device> findAllWithSiteByIdIn(Collection<String> ids);

    /**
     * 그 현장의 그 종류 장비 중 ID가 가장 앞선 것. 후드 연동 판정이 VEMC와 같은 현장의 환기장치를 찾을 때 쓴다(#7).
     *
     * @since 2026-10-24 (#7)
     */
    Optional<Device> findFirstBySiteIdAndTypeOrderByIdAsc(Long siteId, DeviceType type);
}
