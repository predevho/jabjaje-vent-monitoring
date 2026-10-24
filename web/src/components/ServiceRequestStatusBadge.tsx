/**
 * A/S 신청 상태 배지 (#10).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
import type { ServiceRequestStatus } from '../types/serviceRequest.ts'

const LABEL: Record<ServiceRequestStatus, string> = {
  RECEIVED: '접수됨',
  ASSESSED: '소견 완료',
  NEEDS_HUMAN: '사람 확인 필요',
  DECIDED: '판정됨',
}

const CLASS: Record<ServiceRequestStatus, string> = {
  RECEIVED: 'badge-request-received',
  ASSESSED: 'badge-request-assessed',
  NEEDS_HUMAN: 'badge-request-needs-human',
  DECIDED: 'badge-request-decided',
}

export default function ServiceRequestStatusBadge({ status }: { status: ServiceRequestStatus }) {
  return <span className={`badge ${CLASS[status]}`}>{LABEL[status]}</span>
}
