package vent.servicerequest.rule;

/**
 * 기간 안의 후드 연동 확인 결과 수 (#10). 확인 보류는 세지 않는다.
 *
 * @param confirmed  정상
 * @param noResponse 무반응
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationHoodStats(long confirmed, long noResponse) {
}
