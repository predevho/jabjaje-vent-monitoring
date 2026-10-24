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
}

export interface CreateServiceRequest {
  siteId: number
  deviceId: string | null
  customerMessage: string
  customerName: string | null
  contactPhone: string | null
}
