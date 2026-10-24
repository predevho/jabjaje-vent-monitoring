package vent.servicerequest.rule;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상황 요약에 넣는 현장 정보 (스펙 8.2, #10).
 *
 * <p>현장 이름, 고객 이름·주소·연락처는 넣지 않는다. 지역과 건물·장비 정보만 담는다.
 *
 * @param region            지역 (예: 경기 안성시)
 * @param floorAreaM2       바닥면적
 * @param occupants         거주 인원
 * @param targetAirflowCmh  목표 풍량
 * @param minimumAirflowCmh 최소 환기량
 * @param devices           설치된 장비 (종류와 모델)
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
public record SituationSiteProfile(String region, BigDecimal floorAreaM2, int occupants, int targetAirflowCmh,
                                   int minimumAirflowCmh, List<SituationDevice> devices) {

    /** 첫 환기장치의 모델. 없으면 null */
    public String ventilatorModel() {
        return devices.stream().filter(d -> "VENTILATOR".equals(d.type())).map(SituationDevice::model).findFirst().orElse(null);
    }
}
