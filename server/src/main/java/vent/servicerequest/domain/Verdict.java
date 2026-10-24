package vent.servicerequest.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vent.servicerequest.domain.type.VerdictField;

/**
 * 판정 엔티티 ({@code verdict} 테이블).
 *
 * <p>직원이 AI 소견을 보고 확정한 원인, 조치, 고객 안내(용어집). 판정이 있어야만 고객 안내문이 확정된다(ADR-0005, #11).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
@Entity
public class Verdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long serviceRequestId;

    private Long aiAssessmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> causes;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> technicianActions;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> parts;

    private boolean needsVisit;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> recommendedServices;

    private String customerReply;

    private boolean editedFromAi;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<VerdictField> editedFields;

    private String decidedBy;

    private OffsetDateTime decidedAt;

    protected Verdict() {
    }

    /**
     * 판정 하나.
     *
     * @param aiAssessmentId 참고한 AI 소견. 처음부터 판정하면 null
     * @param content        확정한 원인, 조치, 부품, 방문, 권하는 점검, 고객 안내
     * @param editedFields   AI 소견에서 고친 항목. 참고한 소견이 없으면 비어 있다
     */
    public static Verdict decided(Long serviceRequestId, Long aiAssessmentId, VerdictContent content,
                                  List<VerdictField> editedFields, String decidedBy, OffsetDateTime now) {
        Verdict verdict = new Verdict();
        verdict.serviceRequestId = serviceRequestId;
        verdict.aiAssessmentId = aiAssessmentId;
        verdict.causes = content.causes();
        verdict.technicianActions = content.technicianActions();
        verdict.parts = content.parts();
        verdict.needsVisit = content.needsVisit();
        verdict.recommendedServices = content.recommendedServices();
        verdict.customerReply = content.customerReply();
        verdict.editedFields = List.copyOf(editedFields);
        verdict.editedFromAi = aiAssessmentId != null && !editedFields.isEmpty();
        verdict.decidedBy = decidedBy;
        verdict.decidedAt = now;
        return verdict;
    }

    public Long getId() {
        return id;
    }

    public Long getServiceRequestId() {
        return serviceRequestId;
    }

    public Long getAiAssessmentId() {
        return aiAssessmentId;
    }

    public List<String> getCauses() {
        return causes;
    }

    public List<String> getTechnicianActions() {
        return technicianActions;
    }

    public List<String> getParts() {
        return parts;
    }

    public boolean isNeedsVisit() {
        return needsVisit;
    }

    public List<String> getRecommendedServices() {
        return recommendedServices;
    }

    public String getCustomerReply() {
        return customerReply;
    }

    public boolean isEditedFromAi() {
        return editedFromAi;
    }

    public List<VerdictField> getEditedFields() {
        return editedFields;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
