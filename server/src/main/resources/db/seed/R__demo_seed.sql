-- 시연용 시드 데이터: 현장 3곳과 각 현장의 게이트웨이, 장비.
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-23 (#1)
--
-- demo 프로필에서만 적용된다(application-demo.yml, ADR-0011). 운영 DB에는 들어가지 않는다.
-- Flyway 반복 실행 마이그레이션(R__)이라 모든 버전 마이그레이션 뒤에 실행되고, 이 파일이 바뀌면 다시 실행된다.
-- 그래서 여러 번 실행해도 결과가 같도록(멱등) 이미 있는 행은 건너뛴다.

-- 지역은 잡자재 시공 사례 지역을 따랐다.
-- 목표 풍량 계산 근거는 스펙 5.3 (TAB 값 우선, 없으면 최소 환기량을 올림)
INSERT INTO site (id, name, region, floor_area_m2, occupants, target_airflow_cmh, target_airflow_source) VALUES
    (1, '양평 현장',   '경기 양평군',   132.0, 4, 210, 'TAB'),
    (2, '안성 현장',   '경기 안성시',    99.0, 3, 149, 'MINIMUM_AIRFLOW'),
    (3, '서대문 현장', '서울 서대문구', 165.0, 5, 248, 'MINIMUM_AIRFLOW')
ON CONFLICT (id) DO NOTHING;

SELECT setval('site_id_seq', GREATEST(3, (SELECT MAX(id) FROM site)));

-- 등록만 되고 아직 한 번도 연결되지 않은 게이트웨이는 연결 전(PENDING)으로 시작한다
INSERT INTO gateway (id, site_id, connection_state) VALUES
    ('gw-s1', 1, 'PENDING'),
    ('gw-s2', 2, 'PENDING'),
    ('gw-s3', 3, 'PENDING')
ON CONFLICT (id) DO NOTHING;

INSERT INTO device (id, site_id, gateway_id, type, model) VALUES
    ('vent-s1', 1, 'gw-s1', 'VENTILATOR', 'DOMEKT R 300V'),
    ('vemc-s1', 1, 'gw-s1', 'VEMC',       'VEMC'),
    ('vent-s2', 2, 'gw-s2', 'VENTILATOR', 'DOMEKT R 300V'),
    ('vemc-s2', 2, 'gw-s2', 'VEMC',       'VEMC'),
    ('vent-s3', 3, 'gw-s3', 'VENTILATOR', 'DOMEKT R 450V'),
    ('vemc-s3', 3, 'gw-s3', 'VEMC',       'VEMC')
ON CONFLICT (id) DO NOTHING;
