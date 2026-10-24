/**
 * 현장 카드.
 *
 * 현장 하나의 기본 정보, 게이트웨이 상태, 열회수 환기장치의 최신 측정값을 보여준다.
 * 아직 측정값이 없으면 그렇다고 표시한다. 게이트웨이가 연결됨이 아니면 장비 상태를 알 수 없으므로
 * "확인 불가"로 표시하고 마지막 값을 흐리게 보여 준다 (용어집: 확인 불가, #6). 부스트 버튼과 최근 원격 명령 상태를 보여 준다 (#8).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import type { Device, Site } from '../types/site.ts'
import type { Telemetry } from '../types/telemetry.ts'
import BoostControl from './BoostControl.tsx'
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
  /** "마지막 수신 N분 전"을 계산할 기준 시각 (#6) */
  now: Date
}

/** 부스트 버튼을 끌 이유 (#8, 스펙 6.3). 없으면 null */
function boostDisabledReason(site: Site): string | null {
  if (site.gateway?.connectionState !== 'CONNECTED') return '게이트웨이가 연결되지 않아 부스트를 보낼 수 없어요'
  if (!site.gateway.capabilities?.includes('BOOST')) return '이 게이트웨이는 부스트를 지원하지 않아요'
  return null
}

export default function SiteCard({ site, ventilator, telemetry, recentTelemetry, now }: Props) {
  const targetLabel = site.targetAirflowSource === 'TAB' ? 'TAB' : '최소 환기량'
  // 게이트웨이가 연결되지 않은 동안 장비의 현재 상태는 알 수 없다. 장비가 멈췄다는 뜻은 아니다.
  const unknown = site.gateway?.connectionState !== 'CONNECTED'

  return (
    <section className="card">
      <div className="card-header">
        <h2>{site.name}</h2>
        {site.gateway && <ConnectionBadge gateway={site.gateway} now={now} />}
      </div>
      <div className="muted">
        {site.region} · {site.floorAreaM2}m² · {site.occupants}명 · 목표 풍량 {site.targetAirflowCmh} CMH ({targetLabel})
        {site.targetAirflowSource === 'TAB' && ` · 최소 환기량 ${site.minimumAirflowCmh} CMH`}
      </div>
      <div className="muted">열회수 환기장치 {ventilator ? `${ventilator.model} (${ventilator.id})` : '없음'}</div>

      {unknown && (
        <p className="unknown-note">
          확인 불가 · 게이트웨이가 연결되지 않아 지금 상태를 알 수 없어요{telemetry && '. 아래는 마지막으로 받은 값이에요'}
        </p>
      )}
      {telemetry ? (
        <table className={unknown ? 'stale' : undefined}>
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
              <th>바이패스 / 부스트</th>
              <td>
                {telemetry.bypass ? '켜짐' : '꺼짐'} / {telemetry.boost ? '켜짐' : '꺼짐'}
              </td>
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
      {ventilator && (
        <BoostControl
          ventilatorId={ventilator.id}
          targetAirflowCmh={site.targetAirflowCmh}
          disabledReason={boostDisabledReason(site)}
        />
      )}
    </section>
  )
}
