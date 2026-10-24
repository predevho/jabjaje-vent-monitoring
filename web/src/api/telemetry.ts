/**
 * 측정값 API.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { Telemetry } from '../types/telemetry.ts'
import { getJson } from './client.ts'

/** 장비마다 최신 측정값(품질 OK) 하나씩 가져온다. */
export function fetchLatestTelemetry(): Promise<Telemetry[]> {
  return getJson<Telemetry[]>('/api/telemetry/latest')
}

/**
 * 한 장비의 최근 측정값을 최근 측정된 것부터 가져온다. 그래프에 쓴다.
 *
 * @since 2026-10-24 (#3)
 */
export function fetchRecentTelemetry(deviceId: string, limit: number): Promise<Telemetry[]> {
  return getJson<Telemetry[]>(`/api/devices/${encodeURIComponent(deviceId)}/telemetry?limit=${limit}`)
}
