package vent.hood.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import vent.hood.domain.type.DeviceEventType;
import vent.hood.domain.type.HoodInterlockResult;

/**
 * 장비 이벤트 엔티티 ({@code device_event} 테이블).
 *
 * <p>VEMC가 보낸 후드 ON/OFF와, HOOD_ON마다 서버가 확인한 후드 연동 확인 결과를 담는다(#7).
 * 저장은 중복 무시 SQL로 하고({@code DeviceEventInsertRepository}), 이 엔티티는 조회와 확인 기록에 쓴다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#7)
 */
@Entity
public class DeviceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;

    private UUID messageId;

    @Enumerated(EnumType.STRING)
    private DeviceEventType type;

    private OffsetDateTime occurredAt;

    private OffsetDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    private HoodInterlockResult interlockResult;

    private OffsetDateTime judgedAt;

    protected DeviceEvent() {
    }

    /** 확인 결과를 기록한다. 이미 확인했으면 바꾸지 않는다. */
    public void recordJudgement(HoodInterlockResult result, OffsetDateTime judgedAt) {
        if (interlockResult == null) {
            this.interlockResult = result;
            this.judgedAt = judgedAt;
        }
    }

    public boolean isJudged() {
        return interlockResult != null;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public DeviceEventType getType() {
        return type;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public HoodInterlockResult getInterlockResult() {
        return interlockResult;
    }

    public OffsetDateTime getJudgedAt() {
        return judgedAt;
    }
}
