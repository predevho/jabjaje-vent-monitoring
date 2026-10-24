/**
 * A/S 신청 패널: 접수 양식, 최근 신청 목록, 상세 (#10).
 *
 * 시연용으로 직원 화면에 고객 접수 양식을 함께 둔다. 접수하면 바로 접수됨으로 목록에 붙고, AI 소견이 붙으면
 * 소견 완료 또는 사람 확인 필요로 바뀐다. 상세에서 판정하면 판정됨이 된다(#11). 목록은 5초마다 다시 불러온다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#10)
 */
import { useEffect, useState, type FormEvent } from 'react'
import { createServiceRequest, fetchPartCatalog, fetchServiceRequests } from '../api/serviceRequests.ts'
import type { ServiceRequest } from '../types/serviceRequest.ts'
import type { Site } from '../types/site.ts'
import ServiceRequestDetail from './ServiceRequestDetail.tsx'
import ServiceRequestStatusBadge from './ServiceRequestStatusBadge.tsx'

const REFRESH_INTERVAL_MS = 5_000

interface Props {
  sites: Site[]
}

export default function ServiceRequestPanel({ sites }: Props) {
  const [requests, setRequests] = useState<ServiceRequest[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [siteId, setSiteId] = useState<number | null>(null)
  const [deviceId, setDeviceId] = useState('')
  const [message, setMessage] = useState('')
  const [customerName, setCustomerName] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [partCatalog, setPartCatalog] = useState<string[]>([])

  const currentSiteId = siteId ?? sites[0]?.id ?? null
  const currentSite = sites.find((site) => site.id === currentSiteId)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const next = await fetchServiceRequests()
        if (!cancelled) setRequests(next)
      } catch {
        // 목록을 못 불러와도 다음 주기에 다시 부른다
      }
    }

    load()
    const timer = setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [reloadKey])

  useEffect(() => {
    let cancelled = false
    fetchPartCatalog()
      .then((parts) => {
        if (!cancelled) setPartCatalog(parts)
      })
      .catch(() => {
        // 부품 목록이 없으면 판정 양식에 부품 선택만 비어 보인다
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (currentSiteId === null) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createServiceRequest({
        siteId: currentSiteId,
        deviceId: deviceId || null,
        customerMessage: message,
        customerName: customerName || null,
        contactPhone: contactPhone || null,
      })
      setMessage('')
      setSelectedId(created.id)
      setReloadKey((key) => key + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '접수 실패')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card service-request-card">
      <h2>A/S 신청</h2>
      <form className="service-request-form" onSubmit={submit}>
        <label>
          현장
          <select
            value={currentSiteId ?? ''}
            onChange={(e) => {
              setSiteId(Number(e.target.value))
              setDeviceId('')
            }}
          >
            {sites.map((site) => (
              <option key={site.id} value={site.id}>
                {site.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          장비
          <select value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
            <option value="">모름</option>
            {(currentSite?.devices ?? []).map((device) => (
              <option key={device.id} value={device.id}>
                {device.id} ({device.model})
              </option>
            ))}
          </select>
        </label>
        <label className="wide">
          문의 내용
          <textarea value={message} onChange={(e) => setMessage(e.target.value)} rows={3} maxLength={2000}
                    placeholder="예: 요즘 집이 답답하고 환기가 약한 것 같아요" />
        </label>
        <label>
          고객 이름
          <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} maxLength={50} />
        </label>
        <label>
          연락처
          <input value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} maxLength={30} />
        </label>
        <button type="submit" disabled={submitting || !message.trim() || currentSiteId === null}>
          {submitting ? '접수 중…' : '접수'}
        </button>
      </form>
      {error && <div className="error">{error}</div>}

      <table className="service-request-table">
        <thead>
          <tr>
            <th>번호</th>
            <th>현장</th>
            <th>문의</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          {requests.map((request) => (
            <tr key={request.id} className={request.id === selectedId ? 'selected' : 'clickable'} onClick={() => setSelectedId(request.id)}>
              <td>#{request.id}</td>
              <td>{request.siteName}</td>
              <td className="service-request-message">{request.customerMessage.slice(0, 40)}</td>
              <td>
                <ServiceRequestStatusBadge status={request.status} />
              </td>
            </tr>
          ))}
          {requests.length === 0 && (
            <tr>
              <td colSpan={4} className="muted">
                아직 A/S 신청이 없어요
              </td>
            </tr>
          )}
        </tbody>
      </table>
      {selectedId !== null && (
        <ServiceRequestDetail id={selectedId} partCatalog={partCatalog} onDecided={() => setReloadKey((key) => key + 1)} />
      )}
    </section>
  )
}
