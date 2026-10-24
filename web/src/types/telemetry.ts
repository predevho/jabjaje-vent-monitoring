/**
 * 측정값 API 응답 타입.
 *
 * 서버의 TelemetryResponse와 같은 형태다. 시각은 ISO-8601 문자열(UTC)로 온다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */

/** 측정값 품질: 정상, 범위 밖, 시계 오차 (#3) */
export type TelemetryQuality = 'OK' | 'OUT_OF_RANGE' | 'CLOCK_SKEW'

export interface Telemetry {
  deviceId: string
  messageId: string
  /** 장비가 측정한 시각 */
  measuredAt: string
  /** 서버가 받은 시각 */
  receivedAt: string
  co2Ppm: number
  tempC: number
  humidityPct: number
  supplyCmh: number
  exhaustCmh: number
  filterPa: number
  bypass: boolean
  quality: TelemetryQuality
  /** 연결이 끊긴 동안 모아 두었다가 나중에 받은 늦은 측정값인지 (#3) */
  late: boolean
}
