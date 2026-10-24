package vent.command.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vent.command.domain.type.RemoteCommandStatus;
import vent.command.domain.type.RemoteCommandType;

/**
 * 원격 명령 엔티티 ({@code command} 테이블).
 *
 * <p>서버가 게이트웨이를 통해 장비에 보내는 요청 하나(용어집: 원격 명령). ID는 메시지의 commandId와 같다(#8).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Entity
@Table(name = "command")
public class RemoteCommand {

    /** 지금은 사용자 요청만 있다 */
    private static final String REQUESTED_BY_USER = "USER";
    /** {@code reject_reason} 컬럼 길이 (V9) */
    private static final int MAX_REJECT_REASON_LENGTH = 40;

    @Id
    private UUID id;

    private String deviceId;

    @Enumerated(EnumType.STRING)
    private RemoteCommandType type;

    @JdbcTypeCode(SqlTypes.JSON)
    private BoostParams params;

    @Enumerated(EnumType.STRING)
    private RemoteCommandStatus status;

    private String rejectReason;

    private String requestedBy;

    private OffsetDateTime requestedAt;

    private OffsetDateTime sentAt;

    private OffsetDateTime ackedAt;

    /** 만료 시각 (#9). 재시도해도 바뀌지 않는다 */
    private OffsetDateTime expiresAt;

    /** 재시도 횟수 (#9) */
    private int retryCount;

    /** 마지막으로 재시도한 시각 (#9) */
    private OffsetDateTime retriedAt;

    /** 시간 초과 뒤에 온 응답인지 (#9, 스펙 7.2 늦은 응답) */
    private boolean lateAck;

    protected RemoteCommand() {
    }

    /**
     * 게이트웨이로 보내는 부스트 명령.
     *
     * @param expiresAt 만료 시각 (#9). 장비는 이 시각이 지난 명령을 버린다
     */
    public static RemoteCommand sentBoost(String deviceId, BoostParams params, OffsetDateTime now, OffsetDateTime expiresAt) {
        RemoteCommand command = boost(deviceId, params, now);
        command.status = RemoteCommandStatus.SENT;
        command.sentAt = now;
        command.expiresAt = expiresAt;
        return command;
    }

    /** 게이트웨이가 연결되지 않아 보내지 않은 부스트 명령. 요청이 있었다는 기록으로 남긴다 (스펙 7.1). */
    public static RemoteCommand rejectedOfflineBoost(String deviceId, BoostParams params, OffsetDateTime now) {
        RemoteCommand command = boost(deviceId, params, now);
        command.status = RemoteCommandStatus.REJECTED_OFFLINE;
        return command;
    }

    private static RemoteCommand boost(String deviceId, BoostParams params, OffsetDateTime now) {
        RemoteCommand command = new RemoteCommand();
        command.id = UUID.randomUUID();
        command.deviceId = deviceId;
        command.type = RemoteCommandType.BOOST;
        command.params = params;
        command.requestedBy = REQUESTED_BY_USER;
        command.requestedAt = now;
        return command;
    }

    /**
     * 장비가 적용했다는 응답을 반영한다. 응답을 기다리는 중이거나 시간 초과(늦은 응답)일 때만 바꾸고,
     * 그 밖에 이미 끝난 명령이면 아무것도 하지 않는다(중복 응답, 밀려난 명령).
     *
     * @return 바꿨으면 true
     */
    public boolean markApplied(OffsetDateTime ackedAt) {
        if (!acceptsAck()) {
            return false;
        }
        this.lateAck = status == RemoteCommandStatus.TIMEOUT;
        this.status = RemoteCommandStatus.APPLIED;
        this.ackedAt = ackedAt;
        return true;
    }

    /**
     * 장비가 거절했다는 응답을 반영한다. 응답을 기다리는 중이거나 시간 초과(늦은 응답)일 때만 바꾼다.
     *
     * @return 바꿨으면 true
     */
    public boolean markRejected(String reason, OffsetDateTime ackedAt) {
        if (!acceptsAck()) {
            return false;
        }
        this.lateAck = status == RemoteCommandStatus.TIMEOUT;
        this.status = RemoteCommandStatus.REJECTED;
        // 장비가 보낸 값이라 길이를 믿지 않는다. 너무 길면 저장이 실패해 응답이 버려지고 명령이 보냄으로 남는다.
        this.rejectReason = reason == null || reason.length() <= MAX_REJECT_REASON_LENGTH
                ? reason : reason.substring(0, MAX_REJECT_REASON_LENGTH);
        this.ackedAt = ackedAt;
        return true;
    }

    /**
     * 같은 commandId로 다시 보냈다 (#9). 만료 시각은 그대로다. 재시도가 만료를 늘리면 안 된다.
     *
     * @since 2026-10-24 (#9)
     */
    public void markRetried(OffsetDateTime now) {
        retryCount++;
        retriedAt = now;
    }

    /**
     * 재시도 뒤에도 응답이 없어 시간 초과로 바꾼다 (#9). 응답을 기다리는 중일 때만 바꾼다.
     *
     * @return 바꿨으면 true
     * @since 2026-10-24 (#9)
     */
    public boolean markTimedOut() {
        if (status != RemoteCommandStatus.SENT) {
            return false;
        }
        status = RemoteCommandStatus.TIMEOUT;
        return true;
    }

    /**
     * 같은 장비에 새 명령이 와서 밀려났다 (#9, ADR-0002 장비당 대기 명령 하나). 응답을 기다리는 중일 때만 바꾼다.
     *
     * @return 바꿨으면 true
     * @since 2026-10-24 (#9)
     */
    public boolean markSuperseded() {
        if (status != RemoteCommandStatus.SENT) {
            return false;
        }
        status = RemoteCommandStatus.SUPERSEDED;
        return true;
    }

    /** 응답을 받을 수 있는 상태인지: 기다리는 중이거나, 시간 초과 뒤 늦은 응답 */
    private boolean acceptsAck() {
        return status == RemoteCommandStatus.SENT || status == RemoteCommandStatus.TIMEOUT;
    }

    public UUID getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public RemoteCommandType getType() {
        return type;
    }

    public BoostParams getParams() {
        return params;
    }

    public RemoteCommandStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getAckedAt() {
        return ackedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OffsetDateTime getRetriedAt() {
        return retriedAt;
    }

    public boolean isLateAck() {
        return lateAck;
    }
}
