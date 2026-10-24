/**
 * 부스트 버튼과 최근 원격 명령 상태 (#8).
 *
 * 누르면 목표 풍량 × 1.3으로 30분 부스트를 요청한다(서버가 모델 최대 풍량으로 다시 검증한다). 게이트웨이가 연결됨이 아니거나
 * 부스트를 지원하지 않으면 버튼을 끈다(스펙 6.3). 누른 뒤에는 그 명령의 장비 응답이 올 때까지 2초마다 다시 불러오고(최대 30초, 재시도 뒤 시간 초과까지 약 22초),
 * 그동안 버튼을 끈다. 버튼을 끈 이유가 있어도 최근 명령 상태는 함께 보여 준다.
 * 재시도·시간 초과·밀려남·늦은 응답(#9)도 상태로 보여 준다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
import { useEffect, useRef, useState } from 'react'
import { fetchRecentCommands, requestBoost } from '../api/commands.ts'
import type { RemoteCommand } from '../types/command.ts'

const BOOST_DURATION_SEC = 30 * 60
const BOOST_FACTOR = 1.3
const POLL_INTERVAL_MS = 2_000
const POLL_LIMIT_MS = 30_000

const REJECT_REASONS: Record<string, string> = {
  FROST_PROTECTION: '동결 방지 중',
  HOOD_INTERLOCK: '후드 연동 중',
  EXPIRED: '만료됨',
  SUPERSEDED: '새 명령에 밀림',
  INVALID: '잘못된 명령',
}

function describe(command: RemoteCommand): string {
  switch (command.status) {
    case 'SENT':
      return command.retryCount > 0 ? '보냄 · 응답이 없어 다시 보냄' : '보냄 · 장비 응답을 기다리는 중'
    case 'APPLIED':
      return `${command.lateAck ? '늦게 적용됨' : '적용됨'} · 급기·배기 ${command.supplyCmh} CMH, ${command.durationSec / 60}분`
    case 'REJECTED':
      return `${command.lateAck ? '늦게 거절됨' : '거절됨'} · ${REJECT_REASONS[command.rejectReason ?? ''] ?? command.rejectReason ?? '사유 없음'}`
    case 'TIMEOUT':
      return '시간 초과 · 다시 보냈지만 장비 응답이 없어요'
    case 'SUPERSEDED':
      return '새 명령에 밀림'
    case 'REJECTED_OFFLINE':
      return command.notice ?? '지금은 게이트웨이가 연결되지 않아 명령을 보낼 수 없어요'
  }
}

interface Props {
  ventilatorId: string
  targetAirflowCmh: number
  /** 버튼을 끌 이유. 없으면 누를 수 있다 */
  disabledReason: string | null
}

export default function BoostControl({ ventilatorId, targetAirflowCmh, disabledReason }: Props) {
  const [latest, setLatest] = useState<RemoteCommand | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const pollTimer = useRef<number | null>(null)
  const mounted = useRef(true)

  function stopPolling() {
    if (pollTimer.current !== null) window.clearInterval(pollTimer.current)
    pollTimer.current = null
  }

  useEffect(() => {
    mounted.current = true
    fetchRecentCommands(ventilatorId)
      .then((commands) => {
        if (mounted.current) setLatest(commands[0] ?? null)
      })
      .catch(() => undefined)
    return () => {
      mounted.current = false
      stopPolling()
    }
  }, [ventilatorId])

  /** 방금 보낸 명령의 응답이 올 때까지 다시 불러온다. 끝나거나 20초가 지나면 멈춘다. */
  function pollUntilAnswered(commandId: string) {
    stopPolling()
    const startedAt = Date.now()
    pollTimer.current = window.setInterval(async () => {
      const commands = await fetchRecentCommands(ventilatorId).catch(() => null)
      if (!mounted.current) return stopPolling()
      const current = commands?.find((command) => command.commandId === commandId)
      if (current) setLatest(current)
      if ((current && current.status !== 'SENT') || Date.now() - startedAt > POLL_LIMIT_MS) {
        stopPolling()
        setBusy(false)
      }
    }, POLL_INTERVAL_MS)
  }

  async function onBoost() {
    setBusy(true)
    setError(null)
    try {
      const command = await requestBoost(ventilatorId, Math.round(targetAirflowCmh * BOOST_FACTOR), BOOST_DURATION_SEC)
      if (!mounted.current) return
      setLatest(command)
      if (command.status === 'SENT') {
        pollUntilAnswered(command.commandId)
        return
      }
    } catch (e) {
      if (mounted.current) setError(e instanceof Error ? e.message : '부스트 요청 실패')
    }
    if (mounted.current) setBusy(false)
  }

  return (
    <div className="boost">
      <button type="button" onClick={onBoost} disabled={busy || disabledReason !== null} title={disabledReason ?? undefined}>
        부스트 30분
      </button>
      <span className="muted">
        {error ?? (latest ? describe(latest) : '최근 원격 명령 없음')}
        {disabledReason && <span className="boost-disabled"> · {disabledReason}</span>}
      </span>
    </div>
  )
}
