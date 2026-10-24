/**
 * 열린 이상 목록.
 *
 * 규칙, 대상(현장·장비 또는 게이트웨이), 심각도, 발생 시각, 설명을 표로 보여준다.
 * 발생 시각은 판단 근거가 된 측정값의 측정 시각이다 (#4). 게이트웨이 오프라인은 오프라인이 된 시각이다 (#6).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
import type { Anomaly, AnomalyRuleName, AnomalySeverity } from '../types/anomaly.ts'

const RULE_LABELS: Record<AnomalyRuleName, string> = {
  FILTER_CLOGGED: '필터 막힘',
  CO2_HIGH: 'CO2 과다',
  AIRFLOW_BELOW_TARGET: '목표 풍량 미달',
  BYPASS_TOO_LONG: '바이패스 지속',
  GATEWAY_OFFLINE: '게이트웨이 오프라인',
  VEMC_NO_RESPONSE: 'VEMC 무반응',
  COMMAND_TIMEOUT: '원격 명령 시간 초과',
}

const SEVERITY_LABELS: Record<AnomalySeverity, string> = {
  INFO: '정보',
  WARN: '주의',
  CRITICAL: '심각',
}

const TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export default function AnomalyList({ anomalies }: { anomalies: Anomaly[] }) {
  return (
    <section className="card anomaly-card">
      <h2>열린 이상 {anomalies.length}건</h2>
      {anomalies.length === 0 ? (
        <p className="muted">열린 이상이 없어요.</p>
      ) : (
        <table className="anomaly-table">
          <thead>
            <tr>
              <th>심각도</th>
              <th>규칙</th>
              <th>대상</th>
              <th>발생 시각</th>
              <th>설명</th>
            </tr>
          </thead>
          <tbody>
            {anomalies.map((anomaly) => (
              <tr key={anomaly.id}>
                <td>
                  <span className={`badge badge-severity-${anomaly.severity.toLowerCase()}`}>
                    {SEVERITY_LABELS[anomaly.severity]}
                  </span>
                </td>
                <td>{RULE_LABELS[anomaly.rule] ?? anomaly.rule}</td>
                <td>
                  {anomaly.siteName ?? '-'} · {anomaly.deviceId ?? `게이트웨이 ${anomaly.gatewayId}`}
                </td>
                <td>{TIME_FORMAT.format(new Date(anomaly.openedAt))}</td>
                <td className="anomaly-message">{anomaly.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
