/**
 * 판정 양식 (#11).
 *
 * 소견 완료 건은 검증을 통과한 AI 소견으로 미리 채운다. 그대로 보내면 "그대로 판정", 하나라도 바꾸면 "고쳐서 판정"이다.
 * 버튼 문구는 서버와 같은 기준으로 정한다(앞뒤 공백·빈 줄 무시, 원인만 순서까지, 나머지 목록은 순서 무시). 고쳤는지는
 * 서버가 다시 비교해서 기록한다. 사람 확인 필요 건은 빈 양식으로 처음부터 판정한다.
 * 원인·조치·권하는 점검은 한 줄에 하나씩 적고, 부품은 판매 목록에서 고른다(목록에 없는 AI 부품도 체크를 끌 수 있게
 * 보여 준다). 담당자 이름은 브라우저에 기억한다. 판정에 성공하면 버튼을 다시 켜지 않는다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#11)
 */
import { useState, type FormEvent } from 'react'
import { decideServiceRequest } from '../api/serviceRequests.ts'
import type { AssessmentResult, CreateVerdict } from '../types/serviceRequest.ts'

const DECIDED_BY_KEY = 'vent.decidedBy'

function lines(text: string): string[] {
  return text.split('\n').map((line) => line.trim()).filter((line) => line.length > 0)
}

function sameOrdered(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((value, i) => value === b[i])
}

function sameItems(a: string[], b: string[]): boolean {
  return sameOrdered([...a].sort(), [...b].sort())
}

function readDecidedBy(): string {
  try {
    return localStorage.getItem(DECIDED_BY_KEY) ?? ''
  } catch {
    return ''
  }
}

function rememberDecidedBy(name: string) {
  try {
    localStorage.setItem(DECIDED_BY_KEY, name)
  } catch {
    // 기억하지 못해도 판정은 된다
  }
}

interface Props {
  serviceRequestId: number
  /** 미리 채울 AI 소견. 처음부터 판정하면 null */
  draft: AssessmentResult | null
  partCatalog: string[]
  onDecided: () => void
}

export default function VerdictForm({ serviceRequestId, draft, partCatalog, onDecided }: Props) {
  const initial = {
    causes: (draft?.causes ?? []).map((cause) => cause.title).join('\n'),
    actions: (draft?.technicianActions ?? []).join('\n'),
    parts: draft?.parts ?? [],
    needsVisit: draft?.needsVisit ?? false,
    services: (draft?.recommendedServices ?? []).join('\n'),
    reply: draft?.customerReply ?? '',
  }
  const [causes, setCauses] = useState(initial.causes)
  const [actions, setActions] = useState(initial.actions)
  const [parts, setParts] = useState<string[]>(initial.parts)
  const [needsVisit, setNeedsVisit] = useState(initial.needsVisit)
  const [services, setServices] = useState(initial.services)
  const [reply, setReply] = useState(initial.reply)
  const [decidedBy, setDecidedBy] = useState(readDecidedBy)
  const [submitting, setSubmitting] = useState(false)
  const [decided, setDecided] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const changed =
    !sameOrdered(lines(causes), lines(initial.causes)) ||
    !sameItems(lines(actions), lines(initial.actions)) ||
    !sameItems(parts, initial.parts) ||
    needsVisit !== initial.needsVisit ||
    !sameItems(lines(services), lines(initial.services)) ||
    reply.trim() !== initial.reply.trim()
  const label = draft === null ? '판정' : changed ? '고쳐서 판정' : '그대로 판정'
  // 판매 목록 순서로 보여 주고, 목록에 없는 AI 부품도 끌 수 있게 뒤에 붙인다
  const partChoices = [...partCatalog, ...initial.parts.filter((part) => !partCatalog.includes(part))]

  function togglePart(part: string) {
    setParts((current) =>
      current.includes(part) ? current.filter((p) => p !== part) : partChoices.filter((p) => p === part || current.includes(p)),
    )
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    const body: CreateVerdict = {
      causes: lines(causes),
      technicianActions: lines(actions),
      parts,
      needsVisit,
      recommendedServices: lines(services),
      customerReply: reply,
      decidedBy,
    }
    try {
      await decideServiceRequest(serviceRequestId, body)
      rememberDecidedBy(decidedBy.trim())
      setDecided(true)
      onDecided()
    } catch (e) {
      setError(e instanceof Error ? e.message : '판정 실패')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="verdict-form" onSubmit={submit}>
      <h3>판정 {draft ? <span className="muted">· AI 소견으로 미리 채움</span> : <span className="muted">· 처음부터</span>}</h3>
      <label>
        원인 (한 줄에 하나, 가능성 순)
        <textarea value={causes} onChange={(e) => setCauses(e.target.value)} rows={2} />
      </label>
      <label>
        조치 (한 줄에 하나)
        <textarea value={actions} onChange={(e) => setActions(e.target.value)} rows={2} />
      </label>
      <fieldset>
        <legend>부품</legend>
        {partChoices.map((part) => (
          <label key={part} className="inline">
            <input type="checkbox" checked={parts.includes(part)} onChange={() => togglePart(part)} />
            {part}
            {!partCatalog.includes(part) && <span className="muted"> (판매 목록에 없음)</span>}
          </label>
        ))}
      </fieldset>
      <label className="inline">
        <input type="checkbox" checked={needsVisit} onChange={(e) => setNeedsVisit(e.target.checked)} />
        방문 필요
      </label>
      <label>
        권하는 점검 (한 줄에 하나, 예: TAB, 블로어도어 테스트)
        <textarea value={services} onChange={(e) => setServices(e.target.value)} rows={1} />
      </label>
      <label>
        고객 안내문 (판정하면 이 글이 고객에게 확정돼요)
        <textarea value={reply} onChange={(e) => setReply(e.target.value)} rows={3} maxLength={2000} />
      </label>
      <label>
        담당자 이름
        <input value={decidedBy} onChange={(e) => setDecidedBy(e.target.value)} maxLength={50} />
      </label>
      {error && <div className="error">{error}</div>}
      <button type="submit" disabled={submitting || decided}>
        {decided ? '판정됨' : submitting ? '판정 중…' : label}
      </button>
    </form>
  )
}
