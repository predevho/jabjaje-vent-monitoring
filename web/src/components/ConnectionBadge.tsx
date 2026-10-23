/**
 * 게이트웨이 연결 상태 배지.
 *
 * 용어집의 이름(연결 전, 연결됨, 일시 끊김, 오프라인)으로 보여준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { ConnectionState } from '../types/site.ts'

const LABELS: Record<ConnectionState, string> = {
  PENDING: '연결 전',
  CONNECTED: '연결됨',
  INTERRUPTED: '일시 끊김',
  OFFLINE: '오프라인',
}

export default function ConnectionBadge({ state }: { state: ConnectionState }) {
  return <span className={`badge badge-${state.toLowerCase()}`}>게이트웨이 {LABELS[state]}</span>
}
