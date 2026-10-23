package vent.site.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vent.site.dto.response.SiteResponse;
import vent.site.repository.SiteRepository;

/**
 * 현장 조회 유스케이스.
 *
 * <p>현장, 게이트웨이, 장비를 읽기 전용 트랜잭션 안에서 조회해 응답 DTO로 바꾼다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@Service
public class SiteService {

    private final SiteRepository siteRepository;

    public SiteService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    /** 모든 현장을 게이트웨이, 장비와 함께 id 순서로 돌려준다. */
    @Transactional(readOnly = true)
    public List<SiteResponse> findAllSites() {
        return siteRepository.findAllWithGatewayAndDevices().stream()
                .map(SiteResponse::from)
                .toList();
    }
}
