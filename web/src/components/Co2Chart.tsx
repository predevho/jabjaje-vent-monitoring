/**
 * CO2 추이 그래프.
 *
 * 가장 최근 측정 시각 기준 1시간 안의 CO2를 측정 시각 순서로 선 그래프로 그린다. 품질이 OK인 값만 그리고,
 * 연결이 끊긴 동안 쌓였다가 나중에 받은 늦은 측정값은 주황색 점으로 구분한다 (#3).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Telemetry } from '../types/telemetry.ts'

const TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' })
/** 가장 최근 측정 시각에서 이만큼 이전까지만 그린다. 오래 꺼져 있던 구간을 선으로 잇지 않기 위해서다. */
const WINDOW_MS = 60 * 60 * 1000
const COLOR_ON_TIME = '#185FA5'
const COLOR_LATE = '#D85A30'

interface Point {
  time: number
  co2Ppm: number
  late: boolean
}

interface DotProps {
  cx?: number
  cy?: number
  index?: number
  payload?: Point
}

/** 늦은 측정값은 크고 주황색으로, 제때 온 측정값은 작고 파란색으로 그린다. */
function TelemetryDot({ cx, cy, index, payload }: DotProps) {
  if (cx === undefined || cy === undefined || !payload) return null
  return (
    <circle
      key={index}
      cx={cx}
      cy={cy}
      r={payload.late ? 3.5 : 1.5}
      fill={payload.late ? COLOR_LATE : COLOR_ON_TIME}
      stroke="none"
    />
  )
}

export default function Co2Chart({ recentTelemetry }: { recentTelemetry: Telemetry[] }) {
  // 범위 밖·시계 오차 값은 판단에 쓰지 않는 값이라 그래프에도 그리지 않는다 (스펙 5.2).
  const okPoints: Point[] = recentTelemetry
    .filter((telemetry) => telemetry.quality === 'OK')
    .map((telemetry) => ({
      time: new Date(telemetry.measuredAt).getTime(),
      co2Ppm: telemetry.co2Ppm,
      late: telemetry.late,
    }))
    .sort((a, b) => a.time - b.time)
  const newest = okPoints.length > 0 ? okPoints[okPoints.length - 1].time : 0
  const points = okPoints.filter((point) => point.time >= newest - WINDOW_MS)
  const lateCount = points.filter((point) => point.late).length

  if (points.length < 2) {
    return <p className="muted">그래프를 그리기에는 측정값이 아직 적어요.</p>
  }

  return (
    <div className="chart">
      <div className="chart-title">
        CO2 추이 (최근 1시간, {points.length}개)
        <span className="legend">
          <span className="legend-dot" style={{ background: COLOR_ON_TIME }} /> 제때 받음
          <span className="legend-dot" style={{ background: COLOR_LATE }} /> 늦은 측정값 {lateCount}개
        </span>
      </div>
      <ResponsiveContainer width="100%" height={140}>
        <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="#eaeef2" vertical={false} />
          <XAxis
            dataKey="time"
            type="number"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(time: number) => TIME_FORMAT.format(time)}
            tick={{ fontSize: 11, fill: '#59636e' }}
            minTickGap={40}
          />
          <YAxis domain={['auto', 'auto']} tick={{ fontSize: 11, fill: '#59636e' }} width={48} />
          <Tooltip
            labelFormatter={(time) => TIME_FORMAT.format(Number(time))}
            formatter={(value) => [`${value} ppm`, 'CO2']}
          />
          <Line
            type="monotone"
            dataKey="co2Ppm"
            stroke={COLOR_ON_TIME}
            strokeWidth={1.5}
            dot={<TelemetryDot />}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
