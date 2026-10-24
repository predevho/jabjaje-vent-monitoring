/**
 * A/S 신청 상세: 고객 문의와 AI 소견 (#10).
 *
 * 직원 화면이다. 검증을 통과한 최근 시도의 소견(원인, 조치, 부품, 방문 필요, 권하는 점검, 고객 안내문 초안)을 보여 주고,
 * 검증에 걸린 시도는 이유를 함께 보여 준다. AI 소견은 초안이라 고객에게 그대로 나가지 않는다(ADR-0005).
 * 접수됨이면 소견이 붙을 때까지 2초마다 다시 불러온다. 불러오기가 한 번 실패해도 마지막 내용은 그대로 두고 다시 시도한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
import { useEffect, useState } from 'react'
import { fetchServiceRequest } from '../api/serviceRequests.ts'
import type { AiAssessment, ServiceRequest } from '../types/serviceRequest.ts'
import ServiceRequestStatusBadge from './ServiceRequestStatusBadge.tsx'

const POLL_INTERVAL_MS = 2_000

const LIKELIHOOD: Record<string, string> = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }

function AssessmentView({ assessment }: { assessment: AiAssessment }) {
  const result = assessment.result
  return (
    <div className="assessment">
      <h3>AI 소견 (초안)</h3>
      <table className="assessment-table">
        <tbody>
          <tr>
            <th>원인</th>
            <td>
              <ol>
                {(result?.causes ?? []).map((cause) => (
                  <li key={cause.title}>
                    {cause.title} <span className="muted">· 가능성 {LIKELIHOOD[cause.likelihood] ?? cause.likelihood} · 근거 {cause.evidence.join(', ')}</span>
                  </li>
                ))}
              </ol>
            </td>
          </tr>
          <tr>
            <th>조치</th>
            <td>{(result?.technicianActions ?? []).join(', ') || '-'}</td>
          </tr>
          <tr>
            <th>부품</th>
            <td>{(result?.parts ?? []).join(', ') || '-'}</td>
          </tr>
          <tr>
            <th>방문</th>
            <td>{result?.needsVisit ? '필요' : '필요 없음'}</td>
          </tr>
          <tr>
            <th>권하는 점검</th>
            <td>{(result?.recommendedServices ?? []).join(', ') || '-'}</td>
          </tr>
          <tr>
            <th>고객 안내문</th>
            <td>{result?.customerReply ?? '-'}</td>
          </tr>
        </tbody>
      </table>
      <p className="muted">모델 {assessment.model} · {assessment.attempt}번째 시도</p>
    </div>
  )
}

function FailedAttempt({ assessment }: { assessment: AiAssessment }) {
  return (
    <li>
      {assessment.attempt}번째 시도 ({assessment.model}) 검증 실패:
      <ul>
        {assessment.validationErrors.map((error, i) => (
          <li key={i}>
            <code>{error.code}</code> {error.message}
          </li>
        ))}
      </ul>
      {assessment.rawResponse && <pre className="raw-response">{assessment.rawResponse}</pre>}
    </li>
  )
}

interface Props {
  id: number
}

export default function ServiceRequestDetail({ id }: Props) {
  const [detail, setDetail] = useState<ServiceRequest | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function load() {
      try {
        const next = await fetchServiceRequest(id)
        if (cancelled) return
        setDetail(next)
        setError(null)
        if (next.status === 'RECEIVED') timer = window.setTimeout(load, POLL_INTERVAL_MS)
      } catch (e) {
        if (cancelled) return
        setError(e instanceof Error ? e.message : '불러오기 실패')
        timer = window.setTimeout(load, POLL_INTERVAL_MS)
      }
    }

    load()
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [id])

  if (!detail || detail.id !== id) return error ? <div className="error">{error}</div> : <p className="muted">불러오는 중…</p>

  const passed = detail.assessments.find((a) => a.passed)
  const failed = detail.assessments.filter((a) => !a.passed)
  return (
    <div className="service-request-detail">
      {error && <div className="error">{error} · 다시 불러오는 중</div>}
      <div className="card-header">
        <h3>
          #{detail.id} {detail.siteName} {detail.deviceId && `· ${detail.deviceId}`}
        </h3>
        <ServiceRequestStatusBadge status={detail.status} />
      </div>
      <p className="customer-message">{detail.customerMessage}</p>
      <p className="muted">
        {detail.customerName ?? '이름 없음'} · {detail.contactPhone ?? '연락처 없음'} · 접수{' '}
        {new Date(detail.createdAt).toLocaleString('ko-KR')}
      </p>
      {detail.status === 'RECEIVED' && <p className="muted">AI 소견을 만드는 중이에요…</p>}
      {passed && <AssessmentView assessment={passed} />}
      {detail.status === 'NEEDS_HUMAN' && (
        <p className="unknown-note">AI 소견이 검증을 통과하지 못했어요. 담당자가 처음부터 판단해 주세요.</p>
      )}
      {failed.length > 0 && <ul className="failed-attempts">{failed.map((a) => <FailedAttempt key={a.attempt} assessment={a} />)}</ul>}
    </div>
  )
}
