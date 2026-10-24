package vent.servicerequest.rule;

/**
 * 상황 요약에 넣는 장비 하나 (#10). 장비 ID는 넣지 않는다.
 *
 * @param type  종류 (VENTILATOR, VEMC)
 * @param model 모델
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationDevice(String type, String model) {
}
