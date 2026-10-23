package vent.site.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vent.site.dto.response.SiteResponse;
import vent.site.service.SiteService;

/**
 * 현장 조회 API.
 *
 * <p>현장과 그 게이트웨이, 장비를 함께 돌려준다. 조회 로직과 트랜잭션은 {@link SiteService}가 맡는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#1)
 */
@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    /** 모든 현장을 id 순서로 돌려준다. */
    @GetMapping
    public List<SiteResponse> list() {
        return siteService.findAllSites();
    }
}
