/**
 * 원격 명령 API (#8).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
import type { RemoteCommand } from '../types/command.ts'
import { getJson, postJson } from './client.ts'

/** 부스트를 요청한다. 잘못된 요청이면 서버의 안내 문구로 오류가 난다. */
export function requestBoost(deviceId: string, supplyCmh: number, durationSec: number): Promise<RemoteCommand> {
  return postJson<RemoteCommand>(`/api/devices/${encodeURIComponent(deviceId)}/boost`, { supplyCmh, durationSec })
}

/** 한 장비의 최근 원격 명령 (최근 것부터). */
export function fetchRecentCommands(deviceId: string): Promise<RemoteCommand[]> {
  return getJson<RemoteCommand[]>(`/api/devices/${encodeURIComponent(deviceId)}/commands`)
}
