package vent.simulator;

/**
 * 시뮬레이터가 흉내 내는 현장 하나의 설정.
 *
 * @param siteId                현장 ID (토픽의 첫 단계)
 * @param gatewayId             게이트웨이 ID (MQTT 클라이언트 ID로 쓴다)
 * @param ventilatorId          측정값을 보내는 열회수 환기장치의 장비 ID (토픽의 장비ID 단계)
 * @param targetAirflowCmh      그 현장의 목표 풍량. 평소 급기는 이 값 근처에서 움직인다.
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
public record SiteConfig(String siteId, String gatewayId, String ventilatorId, int targetAirflowCmh) {
}
