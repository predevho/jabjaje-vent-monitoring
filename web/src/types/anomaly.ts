/**
 * 이상 API 응답 타입.
 *
 * 서버의 AnomalyResponse와 같은 형태다. 시각은 판단 근거가 된 측정값의 측정 시각(UTC)이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */

/** 이상 규칙 이름. 규칙이 늘면(#5, #6, #7) 여기에 추가한다 */
export type AnomalyRuleName = 'FILTER_CLOGGED'

/** 심각도: 정보, 주의, 심각 */
export type AnomalySeverity = 'INFO' | 'WARN' | 'CRITICAL'

/** 상태: 열림, 해결 */
export type AnomalyStatus = 'OPEN' | 'RESOLVED'

export interface Anomaly {
  id: number
  rule: AnomalyRuleName
  severity: AnomalySeverity
  status: AnomalyStatus
  message: string
  siteId: number | null
  siteName: string | null
  deviceId: string | null
  gatewayId: string | null
  openedAt: string
  resolvedAt: string | null
}
