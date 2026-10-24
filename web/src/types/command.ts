/**
 * 원격 명령 API 응답 타입 (#8).
 *
 * 서버의 RemoteCommandResponse와 같은 형태다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */

/** 원격 명령 상태: 보냄, 적용됨, 거절됨, 게이트웨이가 연결되지 않아 보내지 않음 */
export type RemoteCommandStatus = 'SENT' | 'APPLIED' | 'REJECTED' | 'REJECTED_OFFLINE'

export interface RemoteCommand {
  commandId: string
  deviceId: string
  type: 'BOOST'
  status: RemoteCommandStatus
  supplyCmh: number
  exhaustCmh: number
  durationSec: number
  /** 장비가 거절한 이유. 예: FROST_PROTECTION, HOOD_INTERLOCK */
  rejectReason: string | null
  requestedAt: string
  sentAt: string | null
  ackedAt: string | null
  /** 화면 안내. 게이트웨이가 연결되지 않아 보내지 않았을 때만 */
  notice: string | null
}
