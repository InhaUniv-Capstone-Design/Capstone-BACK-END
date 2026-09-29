-- ============================================================================
-- 약연(藥緣) 통합 DB 스키마 v1.1
-- 기준: DB 테이블 명세서(29개 테이블) + ERD 참고자료
-- 변경: 팀 논의로 아래 2건 "이전 버전 유지"로 확정 반영
--   1) auth_token 테이블 복원 (완전 무상태 JWT → Refresh Token 관리 방식으로)
--   2) app_user.status / deleted_at / last_login_at 복원 (논리 삭제 유지)
-- 총 테이블: 30개 (명세서 29개 + auth_token)
-- 대상: PostgreSQL 16
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- drug.product_name 검색용

-- ============================================================================
-- 1. 계정 · 동의
-- ============================================================================

-- 1-1. app_user
--  [복원] status(ACTIVE/DELETED), deleted_at, last_login_at
--  [신규 반영] updated_at, account_type 값 PATIENT/GUARDIAN (새 명세서 기준)
CREATE TABLE app_user (
    user_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id            VARCHAR(30)  NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,   -- bcrypt/argon2 해시. 평문 절대 저장 금지
    account_type        VARCHAR(10)  NOT NULL CHECK (account_type IN ('PATIENT', 'GUARDIAN')),
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    failed_login_count  SMALLINT     NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);
-- 탈퇴한 login_id는 재사용 가능하게 두되(요구사항에 없으므로), 현재 활성 계정끼리만 중복 금지
CREATE UNIQUE INDEX uq_app_user_login_id_active ON app_user (login_id) WHERE deleted_at IS NULL;

-- 1-2. auth_token [복원] — Refresh Token 전용 최소 테이블
--  Access Token은 무상태 JWT로 발급하되, Refresh Token만 여기 저장해서
--  강제 로그아웃 / 비밀번호 변경 시 전체 무효화 / 재사용 탐지(rotation)를 가능하게 함
CREATE TABLE auth_token (
    token_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_user(user_id),
    token_hash    VARCHAR(255) NOT NULL UNIQUE,  -- 토큰 원문은 저장하지 않고 SHA-256 등 해시만 저장
    device_label  VARCHAR(50),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_token_user ON auth_token(user_id) WHERE revoked_at IS NULL;

-- 1-3. patient
CREATE TABLE patient (
    patient_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT REFERENCES app_user(user_id),
    name                VARCHAR(50) NOT NULL,
    birth_date          DATE NOT NULL,
    phone_enc           BYTEA NOT NULL,   -- 앱/서버 레벨에서 AES-GCM 등으로 암호화 후 저장
    phone_hash          CHAR(64) NOT NULL, -- SHA-256(정규화된 번호) — 조회용, 역산 불가
    created_by_user_id  BIGINT REFERENCES app_user(user_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_patient_user ON patient(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_patient_phone_hash ON patient(phone_hash);
CREATE INDEX idx_patient_created_by ON patient(created_by_user_id);

-- 1-4. terms_version
CREATE TABLE terms_version (
    terms_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terms_type       VARCHAR(20) NOT NULL
                      CHECK (terms_type IN ('TOS','PRIVACY','SENSITIVE_HEALTH','SERVICE_NOTICE','DELEGATION','LEGAL_REP')),
    version          VARCHAR(20) NOT NULL,
    content          TEXT NOT NULL,
    is_required      BOOLEAN NOT NULL DEFAULT true,
    effective_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (terms_type, version)
);

-- 1-5. user_agreement
CREATE TABLE user_agreement (
    user_id           BIGINT NOT NULL REFERENCES app_user(user_id),
    terms_version_id  BIGINT NOT NULL REFERENCES terms_version(terms_version_id),
    agreed            BOOLEAN NOT NULL,
    agreed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, terms_version_id)
);

-- 1-6. phone_verification
CREATE TABLE phone_verification (
    verification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone_hash      CHAR(64) NOT NULL,
    purpose         VARCHAR(20) NOT NULL CHECK (purpose IN ('DELEGATION','LEGAL_REP','REVOKE')),
    code_hash       VARCHAR(255) NOT NULL, -- 인증번호도 해시로만 저장(평문 금지)
    attempt_count   SMALLINT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (attempt_count <= 10)  -- 무차별 대입 방지 상한(값은 팀 정책에 맞게 조정)
);
CREATE INDEX idx_phone_verification_hash ON phone_verification(phone_hash, purpose);

-- 1-7. guardian_link
CREATE TABLE guardian_link (
    link_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guardian_user_id  BIGINT NOT NULL REFERENCES app_user(user_id),
    patient_id        BIGINT NOT NULL REFERENCES patient(patient_id),
    status            VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','ACTIVE','REJECTED','REVOKED')),
    permission_scope  VARCHAR(10) NOT NULL DEFAULT 'READ_WRITE'
                       CHECK (permission_scope IN ('READ_ONLY','READ_WRITE')),
    consent_subject   VARCHAR(10) CHECK (consent_subject IN ('SELF','LEGAL_REP')),
    consented_at      TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 진행 중(PENDING·ACTIVE)인 연동은 보호자-복약자 쌍당 1개만 허용
CREATE UNIQUE INDEX uq_guardian_link_open
    ON guardian_link (guardian_user_id, patient_id)
    WHERE status IN ('PENDING', 'ACTIVE');
CREATE INDEX idx_guardian_link_patient ON guardian_link(patient_id);

-- 1-8. consent_log — append-only (수정·삭제 불가)
CREATE TABLE consent_log (
    consent_log_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id        BIGINT NOT NULL,   -- FK 없음 (탈퇴 후에도 이력 보존)
    guardian_user_id  BIGINT NOT NULL,   -- FK 없음
    link_id           BIGINT NOT NULL,   -- FK 없음
    action            VARCHAR(12) NOT NULL CHECK (action IN ('GRANT','REJECT','REVOKE','SCOPE_CHANGE')),
    consent_subject   VARCHAR(10) NOT NULL CHECK (consent_subject IN ('SELF','LEGAL_REP')),
    legal_rep_name    VARCHAR(50),
    permission_scope  VARCHAR(10) NOT NULL CHECK (permission_scope IN ('READ_ONLY','READ_WRITE')),
    terms_version_id  BIGINT NOT NULL REFERENCES terms_version(terms_version_id),
    verification_id   BIGINT,   -- FK 없음
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (consent_subject <> 'LEGAL_REP' OR legal_rep_name IS NOT NULL)
);
CREATE INDEX idx_consent_log_patient ON consent_log(patient_id);

CREATE OR REPLACE FUNCTION consent_log_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'consent_log는 추가만 가능합니다(append-only). UPDATE/DELETE 금지.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_consent_log_no_update
    BEFORE UPDATE ON consent_log
    FOR EACH ROW EXECUTE FUNCTION consent_log_append_only();

CREATE TRIGGER trg_consent_log_no_delete
    BEFORE DELETE ON consent_log
    FOR EACH ROW EXECUTE FUNCTION consent_log_append_only();

-- ============================================================================
-- 2. 약물 마스터 (DUR)
-- ============================================================================

CREATE TABLE dur_dataset (
    dur_dataset_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    base_date            DATE NOT NULL,
    fetched_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    status               VARCHAR(10) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    total_pages          INTEGER,
    source_record_count  INTEGER,
    graph_edge_count     INTEGER,
    error_message        TEXT
);

CREATE TABLE dur_raw_taboo (
    dur_dataset_id BIGINT NOT NULL REFERENCES dur_dataset(dur_dataset_id) ON DELETE CASCADE,
    page_no        INTEGER NOT NULL,
    row_no         SMALLINT NOT NULL,
    payload        JSONB NOT NULL,
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (dur_dataset_id, page_no, row_no)
);

CREATE TABLE drug (
    drug_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_seq       VARCHAR(20) NOT NULL UNIQUE,
    product_name   VARCHAR(200) NOT NULL,
    manufacturer   VARCHAR(100),
    dur_dataset_id BIGINT REFERENCES dur_dataset(dur_dataset_id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_drug_product_name_trgm ON drug USING GIN (product_name gin_trgm_ops);

CREATE TABLE ingredient (
    ingredient_code    VARCHAR(20) PRIMARY KEY,
    ingredient_name    VARCHAR(200) NOT NULL,
    ingredient_name_en VARCHAR(200),
    dur_dataset_id     BIGINT REFERENCES dur_dataset(dur_dataset_id),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE drug_ingredient (
    drug_id         BIGINT NOT NULL REFERENCES drug(drug_id) ON DELETE CASCADE,
    ingredient_code VARCHAR(20) NOT NULL REFERENCES ingredient(ingredient_code),
    PRIMARY KEY (drug_id, ingredient_code)
);

CREATE TABLE dur_taboo_rule (
    dur_seq            VARCHAR(20) NOT NULL,
    ingredient_a_code  VARCHAR(20) NOT NULL REFERENCES ingredient(ingredient_code),
    ingredient_b_code  VARCHAR(20) NOT NULL REFERENCES ingredient(ingredient_code),
    type_code          VARCHAR(5) NOT NULL CHECK (type_code = 'A'),
    prohbt_content     TEXT NOT NULL,
    remark             TEXT,
    notification_date  DATE,
    source_name        VARCHAR(50) NOT NULL DEFAULT 'MFDS DUR',
    dur_dataset_id     BIGINT NOT NULL REFERENCES dur_dataset(dur_dataset_id),
    PRIMARY KEY (dur_seq, ingredient_a_code, ingredient_b_code),
    CHECK (ingredient_a_code < ingredient_b_code)   -- 코드 작은 쪽을 A로 정렬
);

CREATE TABLE dur_taboo_item (
    item_seq_a        VARCHAR(20) NOT NULL REFERENCES drug(item_seq),
    item_seq_b        VARCHAR(20) NOT NULL REFERENCES drug(item_seq),
    dur_seq           VARCHAR(20) NOT NULL,
    ingredient_code_a VARCHAR(20) NOT NULL,
    ingredient_code_b VARCHAR(20) NOT NULL,
    rule_ingr_lo      VARCHAR(20) GENERATED ALWAYS AS (LEAST(ingredient_code_a, ingredient_code_b)) STORED,
    rule_ingr_hi      VARCHAR(20) GENERATED ALWAYS AS (GREATEST(ingredient_code_a, ingredient_code_b)) STORED,
    PRIMARY KEY (item_seq_a, item_seq_b, dur_seq),
    CHECK (item_seq_a < item_seq_b),
    FOREIGN KEY (dur_seq, rule_ingr_lo, rule_ingr_hi)
        REFERENCES dur_taboo_rule(dur_seq, ingredient_a_code, ingredient_b_code)
);

-- ============================================================================
-- 3. 처방 · 복용 계획
-- ============================================================================

CREATE TABLE prescription (
    prescription_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id         BIGINT NOT NULL REFERENCES patient(patient_id),
    source             VARCHAR(10) NOT NULL CHECK (source IN ('OCR','MANUAL')),
    hospital_name      VARCHAR(100),
    pharmacy_name      VARCHAR(100),
    prescribed_date    DATE,
    image_path         VARCHAR(500),
    image_retained     BOOLEAN NOT NULL DEFAULT false,
    created_by_user_id BIGINT REFERENCES app_user(user_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (image_retained = true OR image_path IS NULL)
);
CREATE INDEX idx_prescription_patient ON prescription(patient_id);

CREATE TABLE patient_medication (
    medication_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id          BIGINT NOT NULL REFERENCES patient(patient_id),
    prescription_id     BIGINT REFERENCES prescription(prescription_id),
    drug_id             BIGINT REFERENCES drug(drug_id),
    input_name          VARCHAR(200) NOT NULL,
    match_status        VARCHAR(10) NOT NULL CHECK (match_status IN ('MATCHED','UNMATCHED')),
    dose_per_intake     NUMERIC(5,2) NOT NULL DEFAULT 1 CHECK (dose_per_intake > 0),
    times_per_day       SMALLINT NOT NULL CHECK (times_per_day BETWEEN 1 AND 3),
    total_days          INTEGER NOT NULL CHECK (total_days > 0),
    start_date          DATE NOT NULL,
    end_date            DATE GENERATED ALWAYS AS (start_date + (total_days - 1)) STORED,
    created_by_user_id  BIGINT REFERENCES app_user(user_id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CHECK ( (match_status = 'MATCHED' AND drug_id IS NOT NULL)
         OR (match_status = 'UNMATCHED' AND drug_id IS NULL) ),
    UNIQUE (medication_id, patient_id)   -- 아래 묶음 FK가 참조
);
CREATE INDEX idx_patient_medication_patient ON patient_medication(patient_id) WHERE deleted_at IS NULL;

CREATE TABLE medication_timing (
    medication_id BIGINT NOT NULL REFERENCES patient_medication(medication_id) ON DELETE CASCADE,
    timing_slot   VARCHAR(10) NOT NULL CHECK (timing_slot IN ('MORNING','LUNCH','DINNER')),
    PRIMARY KEY (medication_id, timing_slot)
);

CREATE TABLE schedule_setting (
    schedule_setting_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id          BIGINT NOT NULL REFERENCES patient(patient_id),
    timing_slot         VARCHAR(10) NOT NULL CHECK (timing_slot IN ('MORNING','LUNCH','DINNER')),
    dose_time           TIME NOT NULL,
    normal_window_min   SMALLINT NOT NULL DEFAULT 30,
    close_after_min     SMALLINT NOT NULL DEFAULT 180,
    effective_from      TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to        TIMESTAMPTZ,
    CHECK (close_after_min > normal_window_min),
    UNIQUE (schedule_setting_id, patient_id, timing_slot)  -- 아래 묶음 FK가 참조
);
-- 복약자 x 시기마다 현재 유효한(effective_to가 빈) 설정은 하나
CREATE UNIQUE INDEX uq_schedule_setting_current
    ON schedule_setting(patient_id, timing_slot) WHERE effective_to IS NULL;

CREATE TABLE scheduled_dose (
    scheduled_dose_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id          BIGINT NOT NULL REFERENCES patient(patient_id),
    medication_id       BIGINT NOT NULL,
    schedule_setting_id BIGINT NOT NULL,
    timing_slot         VARCHAR(10) NOT NULL CHECK (timing_slot IN ('MORNING','LUNCH','DINNER')),
    scheduled_at        TIMESTAMPTZ NOT NULL,
    normal_until        TIMESTAMPTZ NOT NULL,
    close_at            TIMESTAMPTZ NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','NORMAL','DELAYED','MISSED')),
    decided_at          TIMESTAMPTZ,
    CHECK (scheduled_at <= normal_until AND normal_until < close_at),
    UNIQUE (medication_id, scheduled_at),           -- 같은 약, 같은 예정시각 중복 방지
    UNIQUE (scheduled_dose_id, medication_id, timing_slot),  -- intake_event가 참조
    FOREIGN KEY (medication_id, patient_id)
        REFERENCES patient_medication(medication_id, patient_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (schedule_setting_id, patient_id, timing_slot)
        REFERENCES schedule_setting(schedule_setting_id, patient_id, timing_slot)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_scheduled_dose_patient_status ON scheduled_dose(patient_id, status);

-- ============================================================================
-- 4. 병용금기 결과
-- ============================================================================

CREATE TABLE interaction_check (
    check_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id     BIGINT NOT NULL REFERENCES patient(patient_id),
    trigger_type   VARCHAR(12) NOT NULL CHECK (trigger_type IN ('MED_ADD','MED_UPDATE','MED_DELETE','DUR_REFRESH')),
    status         VARCHAR(10) NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    dur_dataset_id BIGINT REFERENCES dur_dataset(dur_dataset_id),
    checked_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_interaction_check_patient ON interaction_check(patient_id, checked_at DESC);

CREATE TABLE interaction_finding (
    finding_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_id          BIGINT NOT NULL REFERENCES interaction_check(check_id) ON DELETE CASCADE,
    medication_a_id   BIGINT NOT NULL REFERENCES patient_medication(medication_id),
    medication_b_id   BIGINT NOT NULL REFERENCES patient_medication(medication_id),
    dur_seq           VARCHAR(20) NOT NULL,
    ingredient_a_code VARCHAR(20) NOT NULL,
    ingredient_b_code VARCHAR(20) NOT NULL,
    source_type_code  VARCHAR(20),
    reason_text       TEXT NOT NULL,
    remark            TEXT,
    source_name       VARCHAR(50) NOT NULL DEFAULT 'MFDS DUR',
    source_base_date  DATE NOT NULL,
    CHECK (medication_a_id < medication_b_id),
    UNIQUE (check_id, medication_a_id, medication_b_id, dur_seq)
);

-- ============================================================================
-- 5. 스마트 약통
-- ============================================================================

CREATE TABLE device (
    device_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    serial_no            VARCHAR(40) NOT NULL,
    secret_hash          VARCHAR(255) NOT NULL,   -- 약통 인증 비밀값도 해시로만 저장
    patient_id           BIGINT NOT NULL REFERENCES patient(patient_id),
    owner_confirmed_by   BIGINT REFERENCES app_user(user_id),
    max_compartments     SMALLINT NOT NULL CHECK (max_compartments BETWEEN 1 AND 3),
    pair_window_sec      SMALLINT NOT NULL DEFAULT 60,
    status               VARCHAR(12) NOT NULL DEFAULT 'REGISTERED' CHECK (status IN ('REGISTERED','UNREGISTERED')),
    firmware_version     VARCHAR(20),
    battery_pct          SMALLINT CHECK (battery_pct BETWEEN 0 AND 100),
    ir_sensor_ok         BOOLEAN,
    button_ok            BOOLEAN,
    last_seen_at         TIMESTAMPTZ,
    schedule_version     INTEGER NOT NULL DEFAULT 0,
    schedule_synced_ver  INTEGER NOT NULL DEFAULT 0,
    registered_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    unregistered_at      TIMESTAMPTZ,
    CHECK (status = 'REGISTERED' OR unregistered_at IS NOT NULL),
    UNIQUE (device_id, patient_id)   -- compartment_mapping이 참조
);
-- 사용 중인 약통은 시리얼 번호마다, 복약자마다 하나
CREATE UNIQUE INDEX uq_device_active_serial
    ON device(serial_no, patient_id) WHERE status = 'REGISTERED';

CREATE TABLE compartment_mapping (
    mapping_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id             BIGINT NOT NULL,
    patient_id            BIGINT NOT NULL REFERENCES patient(patient_id),
    compartment_no        SMALLINT NOT NULL,
    medication_id         BIGINT NOT NULL,
    status                VARCHAR(12) NOT NULL DEFAULT 'PENDING_LOAD'
                           CHECK (status IN ('PENDING_LOAD','ACTIVE','ENDED')),
    loaded_count          NUMERIC(6,2) CHECK (loaded_count IS NULL OR loaded_count >= 0),
    remaining_count       NUMERIC(6,2) CHECK (remaining_count IS NULL OR remaining_count >= 0),
    low_stock_threshold   NUMERIC(6,2) NOT NULL DEFAULT 3,
    low_stock_notified    BOOLEAN NOT NULL DEFAULT false,
    confirmed_by_user_id  BIGINT REFERENCES app_user(user_id),
    valid_from            TIMESTAMPTZ,
    valid_to              TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status = 'PENDING_LOAD' OR (valid_from IS NOT NULL AND loaded_count IS NOT NULL)),
    UNIQUE (mapping_id, patient_id, device_id, compartment_no, medication_id),  -- intake_event가 참조
    FOREIGN KEY (device_id, patient_id)
        REFERENCES device(device_id, patient_id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (medication_id, patient_id)
        REFERENCES patient_medication(medication_id, patient_id) DEFERRABLE INITIALLY DEFERRED
);
-- 한 칸에는 한 약만(현재 유효 배정 기준)
CREATE UNIQUE INDEX uq_compartment_active
    ON compartment_mapping(device_id, compartment_no) WHERE status IN ('PENDING_LOAD','ACTIVE');
-- 한 약은 한 칸에만
CREATE UNIQUE INDEX uq_medication_active_compartment
    ON compartment_mapping(medication_id) WHERE status IN ('PENDING_LOAD','ACTIVE');

CREATE TABLE device_signal (
    signal_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id       BIGINT NOT NULL REFERENCES device(device_id),
    device_event_id VARCHAR(64) NOT NULL,
    signal_type     VARCHAR(12) NOT NULL CHECK (signal_type IN ('IR_TRIGGER','BUTTON_PRESS')),
    compartment_no  SMALLINT,
    occurred_at     TIMESTAMPTZ NOT NULL,
    clock_source    VARCHAR(4) NOT NULL DEFAULT 'NTP' CHECK (clock_source IN ('NTP','RTC')),
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_resent       BOOLEAN NOT NULL DEFAULT false,
    processed_at    TIMESTAMPTZ,
    CHECK ( (signal_type = 'IR_TRIGGER' AND compartment_no IS NOT NULL)
         OR (signal_type = 'BUTTON_PRESS' AND compartment_no IS NULL) ),
    UNIQUE (device_id, device_event_id),                       -- 재전송 중복 제거
    UNIQUE (signal_id, device_id, signal_type, compartment_no), -- intake_event(ir) 참조
    UNIQUE (signal_id, device_id, signal_type)                  -- intake_event(button) 참조
);
CREATE INDEX idx_device_signal_unprocessed ON device_signal(device_id, occurred_at) WHERE processed_at IS NULL;

CREATE TABLE intake_event (
    intake_event_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id         BIGINT NOT NULL REFERENCES patient(patient_id),
    device_id          BIGINT NOT NULL,
    mapping_id         BIGINT NOT NULL,
    compartment_no     SMALLINT NOT NULL,
    medication_id      BIGINT NOT NULL,
    ir_signal_id       BIGINT NOT NULL,
    ir_signal_type     VARCHAR(12) GENERATED ALWAYS AS ('IR_TRIGGER') STORED,
    button_signal_id   BIGINT,
    button_signal_type VARCHAR(12) GENERATED ALWAYS AS
                        (CASE WHEN button_signal_id IS NOT NULL THEN 'BUTTON_PRESS' END) STORED,
    detection          VARCHAR(10) NOT NULL CHECK (detection IN ('CONFIRMED','OPEN_ONLY')),
    occurred_at        TIMESTAMPTZ NOT NULL,
    confirmed_at       TIMESTAMPTZ,
    scheduled_dose_id  BIGINT,
    timing_slot        VARCHAR(10) CHECK (timing_slot IS NULL OR timing_slot IN ('MORNING','LUNCH','DINNER')),
    judgement          VARCHAR(12) NOT NULL CHECK (judgement IN ('NORMAL','DELAYED','UNSCHEDULED','OPEN_ONLY')),
    remaining_after    NUMERIC(6,2),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ( (detection = 'CONFIRMED' AND button_signal_id IS NOT NULL AND confirmed_at IS NOT NULL)
         OR (detection = 'OPEN_ONLY' AND judgement = 'OPEN_ONLY') ),
    CHECK ( (judgement IN ('NORMAL','DELAYED') AND scheduled_dose_id IS NOT NULL AND timing_slot IS NOT NULL)
         OR (judgement NOT IN ('NORMAL','DELAYED')) ),
    UNIQUE (ir_signal_id),        -- IR 신호 하나는 복용 이벤트 하나에만
    UNIQUE (scheduled_dose_id),   -- 예정 복용 하나도 복용 이벤트 하나에만
    FOREIGN KEY (mapping_id, patient_id, device_id, compartment_no, medication_id)
        REFERENCES compartment_mapping(mapping_id, patient_id, device_id, compartment_no, medication_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (ir_signal_id, device_id, ir_signal_type, compartment_no)
        REFERENCES device_signal(signal_id, device_id, signal_type, compartment_no)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (button_signal_id, device_id, button_signal_type)
        REFERENCES device_signal(signal_id, device_id, signal_type)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (scheduled_dose_id, medication_id, timing_slot)
        REFERENCES scheduled_dose(scheduled_dose_id, medication_id, timing_slot)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_intake_event_patient ON intake_event(patient_id, occurred_at DESC);

CREATE TABLE device_status_report (
    report_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id             BIGINT NOT NULL REFERENCES device(device_id),
    reported_at           TIMESTAMPTZ NOT NULL,
    battery_pct           SMALLINT CHECK (battery_pct BETWEEN 0 AND 100),
    ir_sensor_ok          BOOLEAN,
    ir_fault_compartments SMALLINT[],
    button_ok             BOOLEAN,
    firmware_version      VARCHAR(20),
    wifi_rssi             SMALLINT
);
CREATE INDEX idx_device_status_report_device ON device_status_report(device_id, reported_at DESC);

-- ============================================================================
-- 6. 알림
-- ============================================================================

CREATE TABLE push_token (
    push_token_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES app_user(user_id),
    fcm_token               VARCHAR(512) NOT NULL UNIQUE,
    notification_permitted  BOOLEAN NOT NULL DEFAULT true,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_token_user ON push_token(user_id);

CREATE TABLE notification (
    notification_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id           BIGINT NOT NULL REFERENCES patient(patient_id),
    recipient_user_id    BIGINT NOT NULL REFERENCES app_user(user_id),
    notif_type           VARCHAR(20) NOT NULL
                          CHECK (notif_type IN ('INTERACTION_INFO','INTAKE_DETECTED','MISSED_DOSE','LOW_STOCK','DEVICE_STATUS')),
    android_channel      VARCHAR(20) NOT NULL CHECK (android_channel IN ('INTERACTION','INTAKE','DEVICE','STOCK')),
    title                VARCHAR(100) NOT NULL,
    body                 TEXT NOT NULL,
    check_id             BIGINT REFERENCES interaction_check(check_id),
    scheduled_dose_id    BIGINT REFERENCES scheduled_dose(scheduled_dose_id),
    intake_event_id      BIGINT REFERENCES intake_event(intake_event_id),
    mapping_id           BIGINT REFERENCES compartment_mapping(mapping_id),
    device_id            BIGINT REFERENCES device(device_id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at              TIMESTAMPTZ
);
CREATE INDEX idx_notification_recipient ON notification(recipient_user_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE notification_delivery (
    delivery_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id  BIGINT NOT NULL REFERENCES notification(notification_id),
    push_token_id    BIGINT REFERENCES push_token(push_token_id),
    status           VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','SENT','FAILED')),
    attempt_no       SMALLINT NOT NULL DEFAULT 1,
    fcm_message_id   VARCHAR(200),
    error_message    TEXT,
    next_retry_at    TIMESTAMPTZ,
    attempted_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_delivery_retry ON notification_delivery(next_retry_at) WHERE status = 'FAILED';

-- ============================================================================
-- 확인용 쿼리
-- ============================================================================
-- SELECT count(*) FROM information_schema.tables WHERE table_schema='public';  -- 30개 나와야 함
-- SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;
