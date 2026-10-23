/**
 * 측정값 API.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { Telemetry } from '../types/telemetry.ts'
import { getJson } from './client.ts'

/** 장비마다 최신 측정값 하나씩 가져온다. */
export function fetchLatestTelemetry(): Promise<Telemetry[]> {
  return getJson<Telemetry[]>('/api/telemetry/latest')
}
