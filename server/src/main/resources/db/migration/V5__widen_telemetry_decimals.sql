-- 센서 고장값도 저장할 수 있게 온도·습도 컬럼을 넓힌다.
-- @author predevho (AI 보조: Claude)
-- @since 2026-10-24 (#3)
--
-- NUMERIC(4,1)은 999.9까지라 고장 센서가 보내는 65535 같은 값에서 저장 오류가 났다.
-- 범위 밖 값은 버리지 않고 OUT_OF_RANGE로 표시해 저장해야 하므로(스펙 5.2, 9.4 D1) 넉넉하게 넓힌다.
-- 정수 컬럼(INT)은 이미 21억까지 담을 수 있다.
ALTER TABLE telemetry ALTER COLUMN temp_c TYPE NUMERIC(8, 1);
ALTER TABLE telemetry ALTER COLUMN humidity_pct TYPE NUMERIC(8, 1);
