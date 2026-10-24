/**
 * 이상 API.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
import type { Anomaly, AnomalyStatus } from '../types/anomaly.ts'
import { getJson } from './client.ts'

/** 그 상태의 이상을 최근에 생긴 것부터 가져온다. */
export function fetchAnomalies(status: AnomalyStatus): Promise<Anomaly[]> {
  return getJson<Anomaly[]>(`/api/anomalies?status=${status}`)
}
