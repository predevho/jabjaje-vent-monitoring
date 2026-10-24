/**
 * A/S 신청 API (#10).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
import type { CreateServiceRequest, ServiceRequest } from '../types/serviceRequest.ts'
import { getJson, postJson } from './client.ts'

/** A/S 신청을 접수한다. 응답은 바로 오고, AI 소견은 백그라운드에서 붙는다. */
export function createServiceRequest(body: CreateServiceRequest): Promise<ServiceRequest> {
  return postJson<ServiceRequest>('/api/service-requests', body)
}

/** 최근 A/S 신청 20개 (최근 것부터). 소견 시도는 비어 있다. */
export function fetchServiceRequests(): Promise<ServiceRequest[]> {
  return getJson<ServiceRequest[]>('/api/service-requests')
}

/** A/S 신청 하나와 AI 소견 시도들. */
export function fetchServiceRequest(id: number): Promise<ServiceRequest> {
  return getJson<ServiceRequest>(`/api/service-requests/${id}`)
}
