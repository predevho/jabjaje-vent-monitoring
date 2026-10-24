/**
 * A/S 신청 상세: 고객 문의와 AI 소견 (#10).
 *
 * 직원 화면이다. 검증을 통과한 최근 시도의 소견(원인, 조치, 부품, 방문 필요, 권하는 점검, 고객 안내문 초안)을 보여 주고,
 * 검증에 걸린 시도는 이유를 함께 보여 준다. AI 소견은 초안이라 고객에게 그대로 나가지 않는다(ADR-0005).
 * 접수됨이면 소견이 붙을 때까지 2초마다 다시 불러온다. 불러오기가 한 번 실패해도 마지막 내용은 그대로 두고 다시 시도한다.
 * 소견 완료·사람 확인 필요면 판정 양식을, 판정됨이면 판정 내용을 보여 준다(#11). 고객 화면에 실제로 보이는 안내도
 * 고객용 API로 불러와 함께 보여 준다. 판정 전에는 비어 있다(ADR-0005).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
import { useEffect, useState } from 'react'
import { fetchCustomerReply, fetchServiceRequest } from '../api/serviceRequests.ts'
import type {
  AiAssessment,
  AssessmentCauseCategory,
  CustomerReply,
  ServiceRequest,
  Verdict,
  VerdictField,
} from '../types/serviceRequest.ts'
import ServiceRequestStatusBadge from './ServiceRequestStatusBadge.tsx'
import VerdictForm from './VerdictForm.tsx'

const POLL_INTERVAL_MS = 2_000

const LIKELIHOOD: Record<string, string> = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }

const CATEGORY: Record<AssessmentCauseCategory, string> = {
  FILTER_CLOGGED: '필터 막힘',
  AIRFLOW_SHORTAGE: '풍량 부족',
  BYPASS: '바이패스',
  VEMC_NOT_LINKED: 'VEMC 미연동',
  AIRTIGHTNESS: '기밀 불량',
  TARGET_AIRFLOW_MISMATCH: '목표 풍량 불일치',
  NO_EQUIPMENT_PROBLEM: '장비 문제 없음',
  INSUFFICIENT_DATA: '데이터 부족',
}

const FIELD_LABEL: Record<VerdictField, string> = {
  CAUSES: '원인',
  TECHNICIAN_ACTIONS: '조치',
  PARTS: '부품',
  NEEDS_VISIT: '방문',
  RECOMMENDED_SERVICES: '권하는 점검',
  CUSTOMER_REPLY: '고객 안내문',
}

function VerdictView({ verdict }: { verdict: Verdict }) {
  const origin =
    verdict.aiAssessmentId === null
      ? '처음부터 판정'
      : verdict.editedFromAi
        ? `AI 소견을 고쳐서 판정 (${verdict.editedFields.map((f) => FIELD_LABEL[f]).join(', ')})`
        : 'AI 소견을 그대로 판정'
  return (
    <div className="verdict">
      <h3>판정</h3>
      <table className="assessment-table">
        <tbody>
          <tr>
            <th>원인</th>
            <td>{verdict.causes.join(', ')}</td>
          </tr>
          <tr>
            <th>조치</th>
            <td>{verdict.technicianActions.join(', ') || '-'}</td>
          </tr>
          <tr>
            <th>부품</th>
            <td>{verdict.parts.join(', ') || '-'}</td>
          </tr>
          <tr>
            <th>방문</th>
            <td>{verdict.needsVisit ? '필요' : '필요 없음'}</td>
          </tr>
          <tr>
            <th>권하는 점검</th>
            <td>{verdict.recommendedServices.join(', ') || '-'}</td>
          </tr>
          <tr>
            <th>고객 안내 (확정)</th>
            <td className="confirmed-reply">{verdict.customerReply}</td>
          </tr>
        </tbody>
      </table>
      <p className="muted">
        {origin} · {verdict.decidedBy} · {new Date(verdict.decidedAt).toLocaleString('ko-KR')}
      </p>
    </div>
  )
}

function AssessmentView({ assessment }: { assessment: AiAssessment }) {
  const result = assessment.result
  return (
    <div className="assessment">
      <h3>AI 소견 (초안, 고객에게 나가지 않음)</h3>
      <table className="assessment-table">
        <tbody>
          <tr>
            <th>원인</th>
            <td>
              <ol>
                {(result?.causes ?? []).map((cause) => (
                  <li key={cause.title}>
                    {cause.category && <span className="badge badge-category">{CATEGORY[cause.category] ?? cause.category}</span>}{' '}
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
  /** 판정에 고를 수 있는 판매 부품 */
  partCatalog: string[]
  /** 판정한 뒤 목록을 다시 불러오게 알린다 */
  onDecided: () => void
}

export default function ServiceRequestDetail({ id, partCatalog, onDecided }: Props) {
  const [detail, setDetail] = useState<ServiceRequest | null>(null)
  const [customerReply, setCustomerReply] = useState<CustomerReply | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function load() {
      try {
        const [next, reply] = await Promise.all([fetchServiceRequest(id), fetchCustomerReply(id)])
        if (cancelled) return
        setDetail(next)
        setCustomerReply(reply)
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
  }, [id, reloadKey])

  if (!detail || detail.id !== id) return error ? <div className="error">{error}</div> : <p className="muted">불러오는 중…</p>

  // 서버와 같이 검증을 통과한 마지막 시도를 참고 소견으로 쓴다
  const passed = detail.assessments.filter((a) => a.passed).at(-1)
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
      {detail.verdict && <VerdictView verdict={detail.verdict} />}
      {customerReply && (
        <p className="customer-view">
          고객 화면:{' '}
          {customerReply.reply ?? <span className="muted">아직 확정된 안내가 없어요 (판정 전에는 고객에게 나가지 않음)</span>}
        </p>
      )}
      {(detail.status === 'ASSESSED' || detail.status === 'NEEDS_HUMAN') && (
        <VerdictForm
          key={detail.id}
          serviceRequestId={detail.id}
          draft={detail.status === 'ASSESSED' ? (passed?.result ?? null) : null}
          partCatalog={partCatalog}
          onDecided={() => {
            setReloadKey((key) => key + 1)
            onDecided()
          }}
        />
      )}
    </div>
  )
}
