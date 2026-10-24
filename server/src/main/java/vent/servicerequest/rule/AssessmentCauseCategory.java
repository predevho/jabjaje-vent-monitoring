package vent.servicerequest.rule;

/**
 * AI 소견 원인 분류 (#12). 소견 기준표 항목과 1:1로 맞춘다.
 *
 * <p>원인 제목은 매번 문장이 달라 자동 채점이 어렵다. 분류가 있으면 평가 세트가 1순위 원인의 분류만 정답과 비교하면 된다
 * (스펙 8.7). 구조화 출력 스키마와 검증기가 이 이름만 허용한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#12)
 */
public enum AssessmentCauseCategory {
    /** 필터 막힘 */
    FILTER_CLOGGED,
    /** 풍량 부족 (팬 설정, 덕트) */
    AIRFLOW_SHORTAGE,
    /** 바이패스 (여름철 외기 냉방 영향) */
    BYPASS,
    /** VEMC 미연동 (후드 연동 실패) */
    VEMC_NOT_LINKED,
    /** 기밀 불량 → 블로어도어 테스트 권유 */
    AIRTIGHTNESS,
    /** 목표 풍량 불일치 (재실 인원 대비 부족) → TAB 권유 */
    TARGET_AIRFLOW_MISMATCH,
    /** 장비 문제 없음 → 방문 상담 */
    NO_EQUIPMENT_PROBLEM,
    /** 데이터 부족 → 판단 보류 */
    INSUFFICIENT_DATA;

    /** 이 이름의 분류가 있는지. */
    public static boolean isKnown(String name) {
        for (AssessmentCauseCategory category : values()) {
            if (category.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
