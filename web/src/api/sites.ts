/**
 * 현장 API.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { Site } from '../types/site.ts'
import { getJson } from './client.ts'

/** 모든 현장을 게이트웨이, 장비와 함께 가져온다. */
export function fetchSites(): Promise<Site[]> {
  return getJson<Site[]>('/api/sites')
}
