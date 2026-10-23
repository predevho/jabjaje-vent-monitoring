package vent.site;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sites")
class SiteController {

    private final SiteRepository siteRepository;

    SiteController(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SiteResponse> list() {
        return siteRepository.findAllWithGatewayAndDevices().stream()
                .map(SiteResponse::from)
                .toList();
    }
}
