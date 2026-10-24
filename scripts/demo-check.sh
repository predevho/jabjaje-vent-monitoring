#!/usr/bin/env bash
# 시연 점검 스크립트 (스펙 12장, #14).
# @author predevho (AI 보조: Claude)
# @since 2026-10-24 (#14)
#
# 새로 띄운 환경에서 시연 5단계를 자동으로 돌리고 단계마다 결과를 ✓/✗로 출력한다. 면접장에서는 README 순서대로
# 손으로 시연하고, 이 스크립트는 그 전에 "다 되는지" 확인할 때 쓴다.
#
#   docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
#   scripts/demo-check.sh           # 전체 (약 8~10분)
#   scripts/demo-check.sh --quick   # 원격 명령 몰림(4-2)을 건너뜀 (약 6분)
#
# 브로커를 잠깐 멈추고(4단계) 시나리오를 주입하므로 시연 중인 환경에는 돌리지 않는다. 필요한 것: curl, python3, docker.
set -uo pipefail
cd "$(dirname "$0")/.."

API=${API:-http://localhost:8080}
SIM=${SIM:-http://localhost:8090}
QUICK=0
[[ "${1:-}" == "--quick" ]] && QUICK=1
FAILED=0
STARTED=$(date +%s)

say()  { printf '\n\033[1m%s\033[0m\n' "$1"; }
pass() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; FAILED=$((FAILED + 1)); }
info() { printf '    %s\n' "$1"; }

# JSON을 stdin으로 받아 파이썬 식을 계산한다. d가 JSON 값이다.
json_py() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
get() { curl -s --max-time 10 "$API$1"; }
post() {
  local body=${2:-'{}'}
  curl -s --max-time 10 -X POST "$1" -H 'Content-Type: application/json' -d "$body"
}
sql() { docker compose exec -T postgres psql -U vent -d vent -tAc "$1" | tr -d '[:space:]'; }

# wait_until <설명> <초> <명령>: 명령이 성공할 때까지 5초마다 다시 시도한다.
wait_until() {
  local desc=$1 timeout=$2 cmd=$3 start
  start=$(date +%s)
  while (( $(date +%s) - start < timeout )); do
    if eval "$cmd" >/dev/null 2>&1; then
      pass "$desc ($(( $(date +%s) - start ))초)"
      return 0
    fi
    sleep 5
  done
  fail "$desc (${timeout}초 안에 안 됨)"
  return 1
}

say "1. 대시보드에서 현장 3곳의 실시간 상태"
wait_until "서버 응답" 180 '[[ $(get /api/sites | json_py "len(d)") == 3 ]]'
wait_until "게이트웨이 3곳 모두 연결됨" 180 \
  '[[ $(get /api/sites | json_py "sum(1 for s in d if s[\"gateway\"][\"connectionState\"] == \"CONNECTED\")") == 3 ]]'
wait_until "환기장치 3대의 최신 측정값" 120 '[[ $(get /api/telemetry/latest | json_py "len(d)") -ge 3 ]]'
SITE1_VENT=$(get /api/sites | json_py '[x["id"] for s in d if s["id"] == 1 for x in s["devices"] if x["type"] == "VENTILATOR"][0]')
SITE1_TARGET=$(get /api/sites | json_py '[s["targetAirflowCmh"] for s in d if s["id"] == 1][0]')
info "현장 1 환기장치 $SITE1_VENT, 목표 풍량 ${SITE1_TARGET} CMH"

say "2. 필터 막힘 주입 → FILTER_CLOGGED 이상"
post "$SIM/sites/2/filter-clog" >/dev/null
wait_until "현장 2에 FILTER_CLOGGED 열림" 300 \
  '[[ $(get "/api/anomalies?status=OPEN" | json_py "sum(1 for a in d if a[\"rule\"] == \"FILTER_CLOGGED\" and a[\"siteId\"] == 2)") -ge 1 ]]'

say "3. 원격 부스트 → 적용됨"
BOOST=$(python3 -c "print(round($SITE1_TARGET * 1.3))")
post "$API/api/devices/$SITE1_VENT/boost" "{\"supplyCmh\": $BOOST, \"durationSec\": 120}" >/dev/null
wait_until "부스트 ${BOOST} CMH 적용됨" 60 \
  '[[ $(get "/api/devices/'"$SITE1_VENT"'/commands" | json_py "d[0][\"status\"]") == APPLIED ]]'

say "4. 브로커 중지 → 후드 ON → 현장에서 급기 상승 → 브로커 재시작 → 끊긴 구간 채워짐"
T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose stop mosquitto >/dev/null 2>&1 && pass "브로커 중지 (인터넷 끊김 흉내)" || fail "브로커를 멈추지 못함"
sleep 5
post "$SIM/sites/3/hood-on" >/dev/null
info "70초 기다리는 중 (측정값 2개가 게이트웨이 버퍼에 쌓이도록)"
sleep 70
LOGS=$(docker compose logs simulator --since "$T0" 2>&1)
grep -q "후드 켜짐 (급기 올림)" <<<"$LOGS" && pass "시뮬레이터: 후드 켜짐, 현장에서 급기 올림 (서버 없이, ADR-0001)" \
  || fail "시뮬레이터 로그에 후드 켜짐(급기 올림)이 없음"
grep -q "버퍼에 쌓음" <<<"$LOGS" && pass "시뮬레이터: 끊긴 동안 측정값을 버퍼에 쌓음" || fail "시뮬레이터 로그에 버퍼 쌓기가 없음"
docker compose start mosquitto >/dev/null 2>&1 && pass "브로커 재시작" || fail "브로커를 다시 띄우지 못함"
# 끊긴 동안 버퍼에 쌓였던 측정값이 늦게 도착해 저장되는지 본다. 2분 넘게 늦으면 late(늦은 측정값)로도 표시되지만
# 이 점검은 약 75초만 끊으므로 표시 없이 채워진다(ADR-0003)
wait_until "끊긴 구간의 측정값이 늦게 도착해 채워짐" 150 \
  '[[ $(sql "SELECT count(*) FROM telemetry WHERE measured_at > TIMESTAMPTZ '"'$T0'"' AND received_at - measured_at > INTERVAL '"'30 seconds'"'") -ge 2 ]]'
wait_until "끊긴 동안의 후드 ON 이벤트가 서버에 저장됨" 150 \
  '[[ $(sql "SELECT count(*) FROM device_event e JOIN device d ON d.id = e.device_id WHERE d.site_id = 3 AND e.type = '"'HOOD_ON'"' AND e.occurred_at > TIMESTAMPTZ '"'$T0'"'") -ge 1 ]]'
post "$SIM/sites/3/hood-off" >/dev/null

if (( QUICK == 0 )); then
  say "4-2. 원격 명령 몰림: 조용한 끊김 → 부스트 3번 → 복구 → 최신 하나만 적용"
  post "$SIM/sites/1/network-silent" >/dev/null
  sleep 3
  for supply in $((BOOST - 20)) $((BOOST - 10)) "$BOOST"; do
    post "$API/api/devices/$SITE1_VENT/boost" "{\"supplyCmh\": $supply, \"durationSec\": 60}" >/dev/null
    sleep 1
  done
  info "응답이 없어 재시도·시간 초과가 날 때까지 30초 기다림"
  sleep 30
  post "$SIM/sites/1/network-up" >/dev/null
  wait_until "마지막 명령만 적용됨, 앞의 2개는 밀려남" 120 \
    '[[ $(get "/api/devices/'"$SITE1_VENT"'/commands" | json_py "\"ok\" if d[0][\"status\"] == \"APPLIED\" and d[0][\"supplyCmh\"] == '"$BOOST"' and [c[\"status\"] for c in d[1:3]] == [\"SUPERSEDED\", \"SUPERSEDED\"] else \"no\"") == ok ]]'
  LAST=$(get "/api/devices/$SITE1_VENT/commands" | json_py 'json.dumps(d[0])')
  [[ $(json_py 'd["lateAck"]' <<<"$LAST") == True ]] \
    && pass "마지막 명령은 시간 초과 뒤 복구되어 늦게 적용됨 (재시도 $(json_py 'd["retryCount"]' <<<"$LAST")번)" \
    || fail "마지막 명령이 늦게 적용됨으로 표시되지 않음"
fi

say "5. A/S 신청 → AI 소견과 근거 → 검증 결과 → 직원 판정"
REQ=$(post "$API/api/service-requests" '{"siteId": 2, "customerMessage": "요즘 바람이 약해진 것 같아요. 010-1234-5678로 연락 주세요.", "customerName": "시연 고객", "contactPhone": "010-1234-5678"}' | json_py 'd["id"]')
if [[ -z $REQ ]]; then
  fail "A/S 신청 접수 실패"
  say "결과: 실패 ${FAILED}개 (5단계 중단)"
  exit 1
fi
info "A/S 신청 #$REQ 접수"
wait_until "AI 소견이 붙음 (소견 완료 또는 사람 확인 필요)" 420 \
  '[[ $(get "/api/service-requests/'"$REQ"'" | json_py "d[\"status\"]") =~ ^(ASSESSED|NEEDS_HUMAN)$ ]]'
DETAIL=$(get "/api/service-requests/$REQ")
STATUS=$(json_py 'd["status"]' <<<"$DETAIL")
info "상태 $STATUS · $(json_py '"; ".join(str(a["attempt"]) + "번째 " + a["model"] + (" 통과" if a["passed"] else " 검증 실패 " + ",".join(e["code"] for e in a["validationErrors"])) for a in d["assessments"])' <<<"$DETAIL")"
if [[ $STATUS == ASSESSED ]]; then
  # 검증을 통과한 마지막 소견
  RESULT=$(json_py 'json.dumps([a for a in d["assessments"] if a["passed"]][-1]["result"], ensure_ascii=False)' <<<"$DETAIL")
  info "1순위 원인: $(json_py 'd["causes"][0]["category"] + " · " + d["causes"][0]["title"]' <<<"$RESULT")"
  BODY=$(json_py 'json.dumps({"causes": [c["title"] for c in d["causes"]], "technicianActions": d["technicianActions"], "parts": d["parts"], "needsVisit": d["needsVisit"], "recommendedServices": d["recommendedServices"], "customerReply": d["customerReply"], "decidedBy": "시연 점검"}, ensure_ascii=False)' <<<"$RESULT")
else
  BODY='{"causes": ["필터 막힘"], "technicianActions": ["필터 교체"], "parts": [], "needsVisit": false, "recommendedServices": [], "customerReply": "필터 교체 안내를 보내 드릴게요.", "decidedBy": "시연 점검"}'
fi
[[ $(get "/api/service-requests/$REQ/customer-reply" | json_py 'd["reply"]') == None ]] && pass "판정 전에는 고객 안내문이 없음 (ADR-0005)" \
  || fail "판정 전인데 고객 안내문이 있음"
[[ $(post "$API/api/service-requests/$REQ/verdict" "$BODY" | json_py 'd.get("decidedBy")') == "시연 점검" ]] && pass "직원 판정 (${STATUS})" || fail "판정 실패"
[[ $(get "/api/service-requests/$REQ/customer-reply" | json_py 'd["reply"] is not None') == True ]] && pass "판정 뒤 고객 안내문 확정" \
  || fail "판정 뒤에도 고객 안내문이 없음"

post "$SIM/sites/2/filter-replace" >/dev/null
say "결과: 실패 ${FAILED}개, $(( ($(date +%s) - STARTED) / 60 ))분 $(( ($(date +%s) - STARTED) % 60 ))초"
exit $(( FAILED > 0 ))
