package vent.servicerequest.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import vent.servicerequest.domain.VerdictContent;
import vent.servicerequest.domain.type.VerdictField;

/**
 * 판정이 AI 소견을 고쳤는지 비교 (#11).
 *
 * <p>화면이 보내는 "고쳤음" 표시를 믿지 않고 서버가 항목별로 비교한다. 앞뒤 공백만 다른 것은 같은 것으로 본다. 원인은
 * 순서까지 비교하고(순서가 가능성 순위다), 조치·부품·권하는 점검은 순서를 보지 않는다(체크를 껐다 켜서 순서만 바뀐 것은
 * 고친 것이 아니다). 고친 항목은 AI 소견이 어디서 틀리는지 보는 품질 기록이 된다(#12).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
@Component
public class VerdictEditDetector {

    /**
     * @param submitted 직원이 제출한 판정 내용
     * @param ai        참고한 AI 소견의 같은 항목
     * @return 고친 항목 ({@link VerdictField} 순서)
     */
    public List<VerdictField> editedFields(VerdictContent submitted, VerdictContent ai) {
        List<VerdictField> edited = new ArrayList<>();
        addIf(edited, VerdictField.CAUSES, !normalized(submitted.causes()).equals(normalized(ai.causes())));
        addIf(edited, VerdictField.TECHNICIAN_ACTIONS, !sameItems(submitted.technicianActions(), ai.technicianActions()));
        addIf(edited, VerdictField.PARTS, !sameItems(submitted.parts(), ai.parts()));
        addIf(edited, VerdictField.NEEDS_VISIT, submitted.needsVisit() != ai.needsVisit());
        addIf(edited, VerdictField.RECOMMENDED_SERVICES, !sameItems(submitted.recommendedServices(), ai.recommendedServices()));
        addIf(edited, VerdictField.CUSTOMER_REPLY, !Objects.equals(strip(submitted.customerReply()), strip(ai.customerReply())));
        return List.copyOf(edited);
    }

    /** 순서를 보지 않고 같은 항목인지. */
    private static boolean sameItems(List<String> a, List<String> b) {
        return normalized(a).stream().sorted().toList().equals(normalized(b).stream().sorted().toList());
    }

    /** 앞뒤 공백을 지우고 빈 항목을 뺀다. */
    private static List<String> normalized(List<String> values) {
        return values.stream().map(VerdictEditDetector::strip).filter(v -> !v.isEmpty()).toList();
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static void addIf(List<VerdictField> fields, VerdictField field, boolean edited) {
        if (edited) {
            fields.add(field);
        }
    }
}
