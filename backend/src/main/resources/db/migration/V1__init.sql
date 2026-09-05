-- LifeLink schema (PROJECT_SPEC.md §4)

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    phone         VARCHAR(32),
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE donors (
    user_id            BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    full_name          VARCHAR(255) NOT NULL,
    blood_group        VARCHAR(8) NOT NULL,
    lat                DOUBLE PRECISION NOT NULL,
    lng                DOUBLE PRECISION NOT NULL,
    city               VARCHAR(128) NOT NULL,
    radius_km          INT NOT NULL DEFAULT 10,
    available          BOOLEAN NOT NULL DEFAULT false,
    last_donation_date DATE,
    next_eligible_date DATE
);
CREATE INDEX idx_donors_available_by_group ON donors(blood_group) WHERE available = true;

CREATE TABLE hospitals (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    license_no VARCHAR(128) NOT NULL,
    address    VARCHAR(512) NOT NULL,
    lat        DOUBLE PRECISION NOT NULL,
    lng        DOUBLE PRECISION NOT NULL,
    verified   BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE blood_banks (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name     VARCHAR(255) NOT NULL,
    address  VARCHAR(512) NOT NULL,
    lat      DOUBLE PRECISION NOT NULL,
    lng      DOUBLE PRECISION NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE blood_inventory (
    id            BIGSERIAL PRIMARY KEY,
    blood_bank_id BIGINT NOT NULL REFERENCES blood_banks(id) ON DELETE CASCADE,
    blood_group   VARCHAR(8) NOT NULL,
    units         INT NOT NULL DEFAULT 0,
    UNIQUE (blood_bank_id, blood_group)
);

CREATE TABLE requests (
    id           BIGSERIAL PRIMARY KEY,
    hospital_id  BIGINT NOT NULL REFERENCES hospitals(id),
    blood_group  VARCHAR(8) NOT NULL,
    units        INT NOT NULL,
    urgency      VARCHAR(16) NOT NULL,
    needed_by    TIMESTAMPTZ NOT NULL,
    status       VARCHAR(16) NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    escalated_at TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ
);
CREATE INDEX idx_requests_status_needed_by ON requests(status, needed_by);
CREATE INDEX idx_requests_hospital ON requests(hospital_id);

CREATE TABLE request_matches (
    id           BIGSERIAL PRIMARY KEY,
    request_id   BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    donor_id     BIGINT NOT NULL REFERENCES donors(user_id),
    status       VARCHAR(16) NOT NULL,
    distance_km  DOUBLE PRECISION,
    notified_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    UNIQUE (request_id, donor_id)
);
CREATE INDEX idx_request_matches_donor_status ON request_matches(donor_id, status);

CREATE TABLE donations (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL REFERENCES requests(id),
    donor_id      BIGINT REFERENCES donors(user_id),
    blood_bank_id BIGINT REFERENCES blood_banks(id),
    units         INT NOT NULL,
    donated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (donor_id IS NOT NULL OR blood_bank_id IS NOT NULL)
);

CREATE TABLE request_status_history (
    id          BIGSERIAL PRIMARY KEY,
    request_id  BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    from_status VARCHAR(16),
    to_status   VARCHAR(16) NOT NULL,
    changed_by  BIGINT REFERENCES users(id),
    reason      VARCHAR(512),
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_request_status_history_request ON request_status_history(request_id);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(32) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    read       BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, read);
