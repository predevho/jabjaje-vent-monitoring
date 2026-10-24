/**
 * A/S 신청 API 응답 타입 (#10).
 *
 * 서버의 ServiceRequestResponse, AiAssessmentResponse와 같은 형태다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */

/** A/S 신청 상태: 접수됨, 소견 완료, 사람 확인 필요, 판정됨 */
export type ServiceRequestStatus = 'RECEIVED' | 'ASSESSED' | 'NEEDS_HUMAN' | 'DECIDED'

export interface AssessmentCause {
  title: string
  likelihood: 'HIGH' | 'MEDIUM' | 'LOW'
  evidence: string[]
}

/** AI 소견 본문 (스펙 8.4). 검증에 걸린 소견은 항목이 빠져 있을 수 있다 */
export interface AssessmentResult {
  causes?: AssessmentCause[]
  technicianActions?: string[]
  parts?: string[]
  needsVisit?: boolean
  recommendedServices?: string[]
  customerReply?: string
}

export interface AssessmentValidationError {
  code: string
  message: string
}

export interface AiAssessment {
  attempt: number
  passed: boolean
  model: string
  result: AssessmentResult | null
  /** 형식 오류일 때만 응답 원문 */
  rawResponse: string | null
  validationErrors: AssessmentValidationError[]
  /** AI에 보낸 입력 사본 (고객 이름·연락처 없음) */
  inputSnapshot: unknown
  createdAt: string
}

/** 판정에서 AI 소견을 고칠 수 있는 항목 (#11) */
export type VerdictField =
  | 'CAUSES'
  | 'TECHNICIAN_ACTIONS'
  | 'PARTS'
  | 'NEEDS_VISIT'
  | 'RECOMMENDED_SERVICES'
  | 'CUSTOMER_REPLY'

/** 판정 (#11). 직원이 확정한 원인, 조치, 고객 안내 */
export interface Verdict {
  /** 참고한 AI 소견. 처음부터 판정했으면 null */
  aiAssessmentId: number | null
  causes: string[]
  technicianActions: string[]
  parts: string[]
  needsVisit: boolean
  recommendedServices: string[]
  customerReply: string
  editedFromAi: boolean
  /** AI 소견에서 고친 항목 */
  editedFields: VerdictField[]
  decidedBy: string
  decidedAt: string
}

export interface CreateVerdict {
  causes: string[]
  technicianActions: string[]
  parts: string[]
  needsVisit: boolean
  recommendedServices: string[]
  customerReply: string
  decidedBy: string
}

/** 고객에게 보여 줄 안내 (#11). 판정 전이면 reply가 null */
export interface CustomerReply {
  status: ServiceRequestStatus
  reply: string | null
  decidedAt: string | null
}

export interface ServiceRequest {
  id: number
  siteId: number
  siteName: string | null
  deviceId: string | null
  customerMessage: string
  customerName: string | null
  contactPhone: string | null
  status: ServiceRequestStatus
  createdAt: string
  /** 상세 조회에서만 채워진다 */
  assessments: AiAssessment[]
  /** 판정 (#11). 판정 전이거나 목록 조회면 null */
  verdict: Verdict | null
}

export interface CreateServiceRequest {
  siteId: number
  deviceId: string | null
  customerMessage: string
  customerName: string | null
  contactPhone: string | null
}
