-- ============================================================
-- 약연(藥緣) P0 테이블 생성 스크립트
-- 대상: 계정·동의 도메인 (app_user, auth_token, patient,
--       consent_text_version, phone_verification, consent_log, care_link)
-- 기준: DB 설계 문서 3.1
-- ============================================================

-- 1. app_user (앱 계정) — FR-AUTH-001~007
CREATE TABLE app_user (
    user_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id            VARCHAR(30) NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    account_type        VARCHAR(10) NOT NULL CHECK (account_type IN ('SELF', 'GUARDIAN')),
    status              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    failed_login_count  SMALLINT NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);

-- 2. auth_token (자동 로그인 토큰) — FR-AUTH-006
CREATE TABLE auth_token (
    token_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_user(user_id),
    token_hash    VARCHAR(100) NOT NULL UNIQUE,
    device_label  VARCHAR(50),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. patient (복약자) — FR-AUTH-009~018
CREATE TABLE patient (
    patient_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT UNIQUE REFERENCES app_user(user_id),
    display_name        VARCHAR(50) NOT NULL,
    birth_date          DATE NOT NULL,
    phone_enc           BYTEA,
    phone_hash          CHAR(64),
    created_by_user_id  BIGINT REFERENCES app_user(user_id),
    status              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);

-- 4. consent_text_version (동의 문구 버전) — FR-AUTH-013
CREATE TABLE consent_text_version (
    text_version_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consent_type     VARCHAR(20) NOT NULL CHECK (consent_type IN ('TERMS', 'PRIVACY', 'SENSITIVE_HEALTH', 'DELEGATION', 'LEGAL_REP')),
    version_no       INT NOT NULL,
    body             TEXT NOT NULL,
    effective_from   TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (consent_type, version_no)
);

-- 5. phone_verification (문자 인증) — FR-AUTH-009·011·018
CREATE TABLE phone_verification (
    verification_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purpose          VARCHAR(20) NOT NULL CHECK (purpose IN ('DELEGATION', 'LEGAL_REP', 'REVOKE')),
    phone_hash       CHAR(64) NOT NULL,
    code_hash        VARCHAR(100) NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    verified_at      TIMESTAMPTZ,
    attempt_count    SMALLINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. consent_log (동의 이력, 추가 전용) — FR-AUTH-008·013·014, NFR-SEC-003
CREATE TABLE consent_log (
    consent_log_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT REFERENCES app_user(user_id),
    patient_id         BIGINT REFERENCES patient(patient_id),
    guardian_user_id   BIGINT REFERENCES app_user(user_id),
    consent_type       VARCHAR(20) NOT NULL CHECK (consent_type IN ('TERMS', 'PRIVACY', 'SENSITIVE_HEALTH', 'DELEGATION', 'LEGAL_REP')),
    action             VARCHAR(10) NOT NULL CHECK (action IN ('GRANT', 'REVOKE')),
    actor_type         VARCHAR(20) NOT NULL CHECK (actor_type IN ('SELF', 'LEGAL_REPRESENTATIVE')),
    scope              VARCHAR(10) CHECK (scope IN ('VIEW', 'EDIT')),
    text_version_id    BIGINT NOT NULL REFERENCES consent_text_version(text_version_id),
    verification_id    BIGINT REFERENCES phone_verification(verification_id),
    row_hash           CHAR(64),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_consent_subject CHECK (user_id IS NOT NULL OR patient_id IS NOT NULL)
);

-- consent_log는 수정·삭제를 막는 추가 전용(append-only) 테이블
CREATE OR REPLACE FUNCTION consent_log_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'consent_log는 수정 또는 삭제할 수 없습니다 (append-only)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_consent_log_append_only
    BEFORE UPDATE OR DELETE ON consent_log
    FOR EACH ROW EXECUTE FUNCTION consent_log_append_only();

-- 7. care_link (보호자–복약자 연동) — FR-AUTH-012~015, FR-MULTI-*
CREATE TABLE care_link (
    care_link_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guardian_user_id     BIGINT NOT NULL REFERENCES app_user(user_id),
    patient_id           BIGINT NOT NULL REFERENCES patient(patient_id),
    link_type            VARCHAR(20) NOT NULL CHECK (link_type IN ('DELEGATE', 'LEGAL_REPRESENTATIVE')),
    permission            VARCHAR(10) NOT NULL DEFAULT 'VIEW' CHECK (permission IN ('VIEW', 'EDIT')),
    status                VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'REVOKED')),
    consent_log_id        BIGINT REFERENCES consent_log(consent_log_id),
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at          TIMESTAMPTZ,
    revoked_at            TIMESTAMPTZ
);

-- 진행 중(PENDING·ACTIVE)인 연동은 보호자-복약자 쌍당 1개만 허용
CREATE UNIQUE INDEX uq_care_link_open
    ON care_link (guardian_user_id, patient_id)
    WHERE status IN ('PENDING', 'ACTIVE');

-- ============================================================
-- 생성 확인용 쿼리 (실행 후 참고)
-- ============================================================
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'public' ORDER BY table_name;
