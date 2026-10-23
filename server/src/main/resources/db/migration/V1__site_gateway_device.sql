-- 현장: 장비가 설치된 건물 하나 (GLOSSARY: 현장)
CREATE TABLE site (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100)  NOT NULL,
    region                VARCHAR(100)  NOT NULL,
    floor_area_m2         NUMERIC(7, 1) NOT NULL CHECK (floor_area_m2 > 0),
    occupants             INT           NOT NULL CHECK (occupants >= 0),
    target_airflow_cmh    INT           NOT NULL CHECK (target_airflow_cmh > 0),
    target_airflow_source VARCHAR(20)   NOT NULL CHECK (target_airflow_source IN ('TAB', 'MINIMUM_AIRFLOW'))
);

-- 게이트웨이: 현장당 하나인 연결 통로. 장비가 아니다 (스펙 5장, 용어집)
-- 수신 시각, 버전, 지원 기능 컬럼은 쓰는 티켓(#2, #6)에서 마이그레이션으로 추가한다 (ADR-0004)
CREATE TABLE gateway (
    id               VARCHAR(50) PRIMARY KEY,
    site_id          BIGINT      NOT NULL UNIQUE REFERENCES site (id),
    connection_state VARCHAR(20) NOT NULL
        CHECK (connection_state IN ('PENDING', 'CONNECTED', 'INTERRUPTED', 'OFFLINE')),
    -- 장비 FK가 (gateway_id, site_id) 쌍을 참조할 수 있게 한다
    UNIQUE (id, site_id)
);

-- 장비: 열회수 환기장치와 VEMC
CREATE TABLE device (
    id         VARCHAR(50) PRIMARY KEY,
    site_id    BIGINT      NOT NULL REFERENCES site (id),
    gateway_id VARCHAR(50) NOT NULL,
    type       VARCHAR(20) NOT NULL CHECK (type IN ('VENTILATOR', 'VEMC')),
    model      VARCHAR(50) NOT NULL,
    -- 장비의 현장과 그 게이트웨이의 현장이 항상 같도록 막는다
    FOREIGN KEY (gateway_id, site_id) REFERENCES gateway (id, site_id)
);

CREATE INDEX idx_device_site ON device (site_id);
