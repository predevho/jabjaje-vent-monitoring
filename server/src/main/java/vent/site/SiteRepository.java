package vent.site;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SiteRepository extends JpaRepository<Site, Long> {

    /** Loads gateways and devices in the same query so the list does not trigger N+1 selects. */
    @EntityGraph(attributePaths = {"gateway", "devices"})
    @Query("select s from Site s order by s.id")
    List<Site> findAllWithGatewayAndDevices();
}
