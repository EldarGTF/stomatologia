-- Схема CRM стоматологической клиники

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'DOCTOR', 'REGISTRAR', 'PATIENT')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE specialties (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE rooms (
    id     BIGSERIAL PRIMARY KEY,
    number VARCHAR(20)  NOT NULL UNIQUE,
    name   VARCHAR(100) NOT NULL
);

CREATE TABLE doctors (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT UNIQUE REFERENCES users (id) ON DELETE SET NULL,
    full_name    VARCHAR(150) NOT NULL,
    specialty_id BIGINT       NOT NULL REFERENCES specialties (id),
    room_id      BIGINT REFERENCES rooms (id) ON DELETE SET NULL,
    phone        VARCHAR(30),
    email        VARCHAR(100),
    active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE patients (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT UNIQUE REFERENCES users (id) ON DELETE SET NULL,
    last_name   VARCHAR(60)  NOT NULL,
    first_name  VARCHAR(60)  NOT NULL,
    middle_name VARCHAR(60),
    birth_date  DATE,
    phone       VARCHAR(30),
    email       VARCHAR(100),
    address     VARCHAR(255),
    notes       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_last_name ON patients (lower(last_name));

CREATE TABLE services (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(150)   NOT NULL UNIQUE,
    description      VARCHAR(500),
    price            NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    duration_minutes INTEGER        NOT NULL CHECK (duration_minutes > 0),
    active           BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE schedules (
    id          BIGSERIAL PRIMARY KEY,
    doctor_id   BIGINT   NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL,
    CHECK (start_time < end_time),
    UNIQUE (doctor_id, day_of_week)
);

CREATE TABLE appointments (
    id         BIGSERIAL PRIMARY KEY,
    patient_id BIGINT      NOT NULL REFERENCES patients (id),
    doctor_id  BIGINT      NOT NULL REFERENCES doctors (id),
    room_id    BIGINT      NOT NULL REFERENCES rooms (id),
    service_id BIGINT      NOT NULL REFERENCES services (id),
    start_at   TIMESTAMP   NOT NULL,
    end_at     TIMESTAMP   NOT NULL,
    status     VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    notes      VARCHAR(500),
    created_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now(),
    CHECK (end_at > start_at),
    -- Запрет двойной записи: пересекающиеся интервалы у одного врача или кабинета (кроме отменённых)
    CONSTRAINT no_doctor_overlap EXCLUDE USING gist (
        doctor_id WITH =, tsrange(start_at, end_at) WITH &&
    ) WHERE (status <> 'CANCELLED'),
    CONSTRAINT no_room_overlap EXCLUDE USING gist (
        room_id WITH =, tsrange(start_at, end_at) WITH &&
    ) WHERE (status <> 'CANCELLED')
);

CREATE INDEX idx_appointments_start ON appointments (start_at);
CREATE INDEX idx_appointments_patient ON appointments (patient_id);

CREATE TABLE appointment_audit (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT      NOT NULL REFERENCES appointments (id) ON DELETE CASCADE,
    action         VARCHAR(20) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'RESCHEDULE', 'CANCEL', 'STATUS')),
    old_value      TEXT,
    new_value      TEXT,
    changed_by     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    changed_at     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_appointment ON appointment_audit (appointment_id);

CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    number         VARCHAR(30)    NOT NULL UNIQUE,
    appointment_id BIGINT         NOT NULL UNIQUE REFERENCES appointments (id),
    amount         NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    status         VARCHAR(20)    NOT NULL CHECK (status IN ('UNPAID', 'PARTIAL', 'PAID', 'CANCELLED')),
    issued_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT         NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    amount      NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    method      VARCHAR(20)    NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
    paid_at     TIMESTAMP      NOT NULL DEFAULT now(),
    received_by BIGINT REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_payments_paid_at ON payments (paid_at);
