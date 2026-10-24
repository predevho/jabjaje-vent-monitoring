package vent.servicerequest.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import vent.servicerequest.domain.Verdict;
import vent.servicerequest.domain.type.VerdictField;

/**
 * 판정 응답 (#11).
 *
 * @param aiAssessmentId 참고한 AI 소견. 처음부터 판정했으면 null
 * @param editedFromAi   AI 소견을 고쳤는지
 * @param editedFields   고친 항목
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
public record VerdictResponse(Long aiAssessmentId, List<String> causes, List<String> technicianActions, List<String> parts,
                              boolean needsVisit, List<String> recommendedServices, String customerReply,
                              boolean editedFromAi, List<VerdictField> editedFields, String decidedBy, OffsetDateTime decidedAt) {

    public static VerdictResponse from(Verdict v) {
        return new VerdictResponse(v.getAiAssessmentId(), v.getCauses(), v.getTechnicianActions(), v.getParts(),
                v.isNeedsVisit(), v.getRecommendedServices(), v.getCustomerReply(), v.isEditedFromAi(), v.getEditedFields(),
                v.getDecidedBy(), v.getDecidedAt());
    }
}
