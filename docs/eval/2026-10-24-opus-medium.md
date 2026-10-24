# AI 소견 평가: opus-medium

<!-- 평가 세트 실행 결과 (스펙 8.7, #12). AssessmentEvalRunner가 만든다. -->

| 항목 | 값 |
|---|---|
| 날짜 | 2026-10-24 |
| AI | Claude |
| 1차 모델 / 재시도 모델 | claude-opus-5-5 / claude-opus-5-5 |
| effort | medium |
| 프롬프트 | v1 |
| 반복 | 시나리오마다 3번 |

## 결과

- 1순위 원인 정답률: **15/15** (100%)
- 검증 통과율: 15/15
- 재시도한 실행: 0
- 응답 시간: 평균 7.5초, 최대 12.3초
- 토큰: 입력 8,163 · 출력 10,220 · 캐시 읽기 47,430 · 캐시 쓰기 0
- 추정 비용: $0.247

## 시나리오별

| 시나리오 | 정답 | 맞음 | 검증 통과 | 1순위 분류 | 평균 시간 |
|---|---|---|---|---|---|
| 필터 차압이 7일간 계속 상승 | FILTER_CLOGGED | 3/3 | 3/3 | FILTER_CLOGGED ×3 | 5.6초 |
| 인원 과다로 CO2 상승, 장비는 정상 | TARGET_AIRFLOW_MISMATCH | 3/3 | 3/3 | TARGET_AIRFLOW_MISMATCH ×3 | 11.4초 |
| 후드 ON에도 급기 변화 없음 | VEMC_NOT_LINKED | 3/3 | 3/3 | VEMC_NOT_LINKED ×3 | 4.7초 |
| 데이터는 정상인데 고객이 답답함 호소 | NO_EQUIPMENT_PROBLEM 또는 AIRTIGHTNESS | 3/3 | 3/3 | AIRTIGHTNESS ×1, NO_EQUIPMENT_PROBLEM ×2 | 10.8초 |
| 게이트웨이 장기 오프라인 | INSUFFICIENT_DATA | 3/3 | 3/3 | INSUFFICIENT_DATA ×3 | 4.9초 |
