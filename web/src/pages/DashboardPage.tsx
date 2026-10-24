/**
 * 대시보드 화면.
 *
 * 현장 목록, 장비별 최신 측정값, 환기장치별 최근 측정값(그래프용)을 불러와 현장 카드로 보여주고,
 * 측정 주기(30초)마다 다시 불러온다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import { useEffect, useState } from 'react'
import { fetchSites } from '../api/sites.ts'
import { fetchLatestTelemetry, fetchRecentTelemetry } from '../api/telemetry.ts'
import SiteCard from '../components/SiteCard.tsx'
import type { Device, Site } from '../types/site.ts'
import type { Telemetry } from '../types/telemetry.ts'

const REFRESH_INTERVAL_MS = 30_000
/** 그래프에 쓸 최근 측정값 개수. 30초 주기로 1시간 분량 */
const RECENT_LIMIT = 120

function findVentilator(site: Site): Device | undefined {
  return site.devices.find((device) => device.type === 'VENTILATOR')
}

export default function DashboardPage() {
  const [sites, setSites] = useState<Site[]>([])
  const [latestByDevice, setLatestByDevice] = useState<Map<string, Telemetry>>(new Map())
  const [recentByDevice, setRecentByDevice] = useState<Map<string, Telemetry[]>>(new Map())
  const [error, setError] = useState<string | null>(null)
  const [loadedAt, setLoadedAt] = useState<Date | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const [nextSites, latest] = await Promise.all([fetchSites(), fetchLatestTelemetry()])
        const ventilatorIds = nextSites.map(findVentilator).flatMap((device) => (device ? [device.id] : []))
        const recentLists = await Promise.all(ventilatorIds.map((id) => fetchRecentTelemetry(id, RECENT_LIMIT)))
        if (cancelled) return
        setSites(nextSites)
        setLatestByDevice(new Map(latest.map((telemetry) => [telemetry.deviceId, telemetry])))
        setRecentByDevice(new Map(ventilatorIds.map((id, i) => [id, recentLists[i]])))
        setLoadedAt(new Date())
        setError(null)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      }
    }

    load()
    const timer = setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  return (
    <main>
      <h1>환기 모니터링</h1>
      <p className="subtitle">
        현장 {sites.length}곳 · 30초마다 갱신
        {loadedAt && ` · 마지막 갱신 ${loadedAt.toLocaleTimeString('ko-KR')}`}
      </p>
      {error && <div className="error">{error}</div>}
      <div className="site-grid">
        {sites.map((site) => {
          const ventilator = findVentilator(site)
          return (
            <SiteCard
              key={site.id}
              site={site}
              ventilator={ventilator}
              telemetry={ventilator && latestByDevice.get(ventilator.id)}
              recentTelemetry={(ventilator && recentByDevice.get(ventilator.id)) ?? []}
            />
          )
        })}
      </div>
    </main>
  )
}
