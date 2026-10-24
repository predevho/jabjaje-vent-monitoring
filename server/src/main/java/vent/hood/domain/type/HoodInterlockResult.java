package vent.hood.domain.type;

/**
 * 후드 연동 확인 결과.
 *
 * <p>HOOD_ON 이벤트마다 같은 현장 환기장치의 급기가 실제로 올랐는지 서버가 확인한 결과다(스펙 6.2, #7).
 * 아직 확인하지 않은 이벤트는 결과가 없다(DB에서 null).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
public enum HoodInterlockResult {
    /** 정상: 60초 뒤 급기가 직전보다 기준(10%) 넘게 올랐다 */
    CONFIRMED,
    /** 무반응: 60초 뒤에도 급기가 오르지 않았다. VEMC_NO_RESPONSE 이상을 만든다 */
    NO_RESPONSE,
    /** 확인 보류: 비교할 측정값이 없거나 후드를 너무 빨리 꺼서 판단할 수 없다. 이상을 만들지도 해결하지도 않는다 */
    INCONCLUSIVE
}
