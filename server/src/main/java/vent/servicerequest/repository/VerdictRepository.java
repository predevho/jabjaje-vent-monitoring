package vent.servicerequest.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vent.servicerequest.domain.Verdict;

/**
 * 판정 저장소.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public interface VerdictRepository extends JpaRepository<Verdict, Long> {

    /** 그 신청의 판정. 신청당 하나다. */
    Optional<Verdict> findByServiceRequestId(Long serviceRequestId);
}
