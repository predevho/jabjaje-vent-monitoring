/**
 * 게이트웨이 연결 상태 배지.
 *
 * 용어집의 이름(연결 전, 연결됨, 일시 끊김, 오프라인)으로 보여준다.
 * 일시 끊김·오프라인이면 "마지막 수신 N분 전"을 붙이고, 연결 전이면 "아직 받은 적 없음"을 붙인다 (스펙 6.3, #6).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { Gateway } from '../types/site.ts'

const LABELS: Record<Gateway['connectionState'], string> = {
  PENDING: '연결 전',
  CONNECTED: '연결됨',
  INTERRUPTED: '일시 끊김',
  OFFLINE: '오프라인',
}

/** 마지막 수신이 몇 분 전인지. 1분이 안 되면 "방금" */
function minutesAgo(iso: string, now: Date): string {
  const minutes = Math.floor((now.getTime() - new Date(iso).getTime()) / 60_000)
  return minutes < 1 ? '방금' : `${minutes}분 전`
}

interface Props {
  gateway: Gateway
  /** "N분 전"을 계산할 기준 시각. 마지막으로 불러온 시각을 쓴다 */
  now: Date
}

export default function ConnectionBadge({ gateway, now }: Props) {
  const state = gateway.connectionState
  let detail = ''
  if (state === 'PENDING') {
    detail = ' · 아직 받은 적 없음'
  } else if (state !== 'CONNECTED' && gateway.lastReceivedAt) {
    detail = ` · 마지막 수신 ${minutesAgo(gateway.lastReceivedAt, now)}`
  }
  return (
    <span className={`badge badge-${state.toLowerCase()}`}>
      게이트웨이 {LABELS[state]}
      {detail}
    </span>
  )
}
