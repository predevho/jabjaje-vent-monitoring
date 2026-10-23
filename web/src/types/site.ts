/**
 * 현장 API 응답 타입.
 *
 * 서버의 SiteResponse, GatewayResponse, DeviceResponse와 같은 형태다. 서버 DTO가 바뀌면 여기서 타입 오류가 난다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */

/** 게이트웨이 연결 상태: 연결 전, 연결됨, 일시 끊김, 오프라인 */
export type ConnectionState = 'PENDING' | 'CONNECTED' | 'INTERRUPTED' | 'OFFLINE'

/** 장비 종류: 열회수 환기장치, VEMC */
export type DeviceType = 'VENTILATOR' | 'VEMC'

/** 목표 풍량의 출처: TAB으로 맞춘 값, 최소 환기량 */
export type TargetAirflowSource = 'TAB' | 'MINIMUM_AIRFLOW'

export interface Gateway {
  id: string
  connectionState: ConnectionState
}

export interface Device {
  id: string
  type: DeviceType
  model: string
}

export interface Site {
  id: number
  name: string
  region: string
  floorAreaM2: number
  occupants: number
  targetAirflowCmh: number
  targetAirflowSource: TargetAirflowSource
  gateway: Gateway | null
  devices: Device[]
}
