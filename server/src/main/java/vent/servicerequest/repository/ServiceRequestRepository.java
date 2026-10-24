package vent.servicerequest.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vent.servicerequest.domain.ServiceRequest;

/**
 * A/S 신청 저장소.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    /** 최근 신청부터. */
    List<ServiceRequest> findAllByOrderByCreatedAtDesc(Limit limit);

    /**
     * AI 소견 처리를 맡는다. 접수됨이고 아무도 맡지 않았거나 맡은 지 오래된 건만 맡는다. 한 줄 UPDATE라 두 곳이 동시에
     * 맡으려 해도 한 곳만 1을 받는다.
     *
     * @return 맡았으면 1, 아니면 0
     */
    @Modifying
    @Query("""
            update ServiceRequest r set r.assessmentStartedAt = :now
            where r.id = :id and r.status = vent.servicerequest.domain.type.ServiceRequestStatus.RECEIVED
              and (r.assessmentStartedAt is null or r.assessmentStartedAt < :staleBefore)
            """)
    int claimForAssessment(@Param("id") Long id, @Param("now") OffsetDateTime now, @Param("staleBefore") OffsetDateTime staleBefore);

    /** 접수된 채로 남은 신청의 ID. 재처리 스케줄러가 쓴다. */
    @Query("""
            select r.id from ServiceRequest r
            where r.status = vent.servicerequest.domain.type.ServiceRequestStatus.RECEIVED and r.createdAt < :before
            order by r.createdAt
            """)
    List<Long> findReceivedIdsCreatedBefore(@Param("before") OffsetDateTime before);
}
