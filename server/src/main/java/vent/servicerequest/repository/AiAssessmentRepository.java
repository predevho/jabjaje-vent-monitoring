package vent.servicerequest.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vent.servicerequest.domain.AiAssessment;

/**
 * AI 소견 저장소.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public interface AiAssessmentRepository extends JpaRepository<AiAssessment, Long> {

    /** 그 신청의 시도 수. */
    long countByServiceRequestId(Long serviceRequestId);

    /** 그 신청에 검증을 통과한 시도가 있는지. 서버가 통과 직후 꺼졌다 다시 맡은 경우를 가린다. */
    boolean existsByServiceRequestIdAndPassedTrue(Long serviceRequestId);

    /** 그 신청에서 검증을 통과한 시도. 판정이 참고하는 AI 소견이다(#11). 통과한 시도는 하나다. */
    Optional<AiAssessment> findFirstByServiceRequestIdAndPassedTrueOrderByAttemptDesc(Long serviceRequestId);

    /** 그 신청의 시도들, 시도 순서대로. */
    List<AiAssessment> findByServiceRequestIdOrderByAttemptAsc(Long serviceRequestId);
}
