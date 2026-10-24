/**
 * A/S 신청 API (#10).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
import type { CreateServiceRequest, CreateVerdict, CustomerReply, ServiceRequest, Verdict } from '../types/serviceRequest.ts'
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

/** 판정한다 (#11). 지금 판정할 수 없거나 잘못된 내용이면 서버의 안내 문구로 오류가 난다. */
export function decideServiceRequest(id: number, body: CreateVerdict): Promise<Verdict> {
  return postJson<Verdict>(`/api/service-requests/${id}/verdict`, body)
}

/** 고객에게 보여 줄 안내 (#11). 판정 전이면 안내문이 없다. */
export function fetchCustomerReply(id: number): Promise<CustomerReply> {
  return getJson<CustomerReply>(`/api/service-requests/${id}/customer-reply`)
}

/** 판정에 고를 수 있는 판매 부품 목록 (#11). */
export function fetchPartCatalog(): Promise<string[]> {
  return getJson<string[]>('/api/service-requests/parts')
}
