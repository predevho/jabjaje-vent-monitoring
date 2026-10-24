/**
 * 현장 카드.
 *
 * 현장 하나의 기본 정보, 게이트웨이 상태, 열회수 환기장치의 최신 측정값을 보여준다.
 * 아직 측정값이 없으면 그렇다고 표시한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { Device, Site } from '../types/site.ts'
import type { Telemetry } from '../types/telemetry.ts'
import Co2Chart from './Co2Chart.tsx'
import ConnectionBadge from './ConnectionBadge.tsx'

const TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

interface Props {
  site: Site
  /** 이 현장의 열회수 환기장치. 없으면 undefined */
  ventilator: Device | undefined
  /** 그 환기장치의 최신 측정값. 아직 없으면 undefined */
  telemetry: Telemetry | undefined
  /** 그 환기장치의 최근 측정값 (그래프용, #3) */
  recentTelemetry: Telemetry[]
}

export default function SiteCard({ site, ventilator, telemetry, recentTelemetry }: Props) {
  const targetLabel = site.targetAirflowSource === 'TAB' ? 'TAB' : '최소 환기량'

  return (
    <section className="card">
      <div className="card-header">
        <h2>{site.name}</h2>
        {site.gateway && <ConnectionBadge state={site.gateway.connectionState} />}
      </div>
      <div className="muted">
        {site.region} · {site.floorAreaM2}m² · {site.occupants}명 · 목표 풍량 {site.targetAirflowCmh} CMH ({targetLabel})
      </div>
      <div className="muted">열회수 환기장치 {ventilator ? `${ventilator.model} (${ventilator.id})` : '없음'}</div>

      {telemetry ? (
        <table>
          <tbody>
            <tr>
              <th>CO2</th>
              <td>{telemetry.co2Ppm} ppm</td>
            </tr>
            <tr>
              <th>온도 / 습도</th>
              <td>
                {telemetry.tempC} ℃ / {telemetry.humidityPct} %
              </td>
            </tr>
            <tr>
              <th>급기 / 배기</th>
              <td>
                {telemetry.supplyCmh} / {telemetry.exhaustCmh} CMH
              </td>
            </tr>
            <tr>
              <th>필터 차압</th>
              <td>{telemetry.filterPa} Pa</td>
            </tr>
            <tr>
              <th>바이패스</th>
              <td>{telemetry.bypass ? '켜짐' : '꺼짐'}</td>
            </tr>
            <tr>
              <th>측정 시각</th>
              <td>{TIME_FORMAT.format(new Date(telemetry.measuredAt))}</td>
            </tr>
          </tbody>
        </table>
      ) : (
        <p className="muted">아직 받은 측정값이 없어요.</p>
      )}
      {telemetry && <Co2Chart recentTelemetry={recentTelemetry} />}
    </section>
  )
}
