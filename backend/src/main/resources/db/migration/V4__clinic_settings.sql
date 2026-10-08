-- Настройки клиники: реквизиты, правила записи и счетов. Всегда ровно одна строка (id = 1).
CREATE TABLE clinic_settings (
    id                   SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    name                 VARCHAR(200)   NOT NULL,
    address              VARCHAR(300)   NOT NULL,
    phone                VARCHAR(50)    NOT NULL,
    email                VARCHAR(100),
    bin                  VARCHAR(12),
    bank_name            VARCHAR(200),
    iik                  VARCHAR(34),
    bik                  VARCHAR(11),
    slot_step_minutes    INT            NOT NULL DEFAULT 15 CHECK (slot_step_minutes IN (10, 15, 20, 30)),
    booking_horizon_days INT            NOT NULL DEFAULT 60 CHECK (booking_horizon_days BETWEEN 7 AND 365),
    min_lead_hours       INT            NOT NULL DEFAULT 1 CHECK (min_lead_hours BETWEEN 0 AND 72),
    patient_cancel_hours INT            NOT NULL DEFAULT 24 CHECK (patient_cancel_hours BETWEEN 0 AND 168),
    invoice_prefix       VARCHAR(10)    NOT NULL DEFAULT 'СЧ',
    vat_enabled          BOOLEAN        NOT NULL DEFAULT FALSE,
    vat_rate             NUMERIC(4, 2)  NOT NULL DEFAULT 12 CHECK (vat_rate >= 0 AND vat_rate < 100),
    prepayment_threshold NUMERIC(10, 2) CHECK (prepayment_threshold > 0),
    updated_at           TIMESTAMP      NOT NULL DEFAULT now(),
    updated_by           BIGINT REFERENCES users (id) ON DELETE SET NULL
);

INSERT INTO clinic_settings (id, name, address, phone, email)
VALUES (1, 'Стоматологическая клиника «Улыбка»', 'г. Павлодар, ул. Сатпаева, д. 1', '+7 (7182) 00-00-00',
        'info@ulybka.kz');

-- Нерабочие дни клиники: в эти даты запись на приём закрыта.
CREATE TABLE holidays (
    id   BIGSERIAL PRIMARY KEY,
    day  DATE         NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

INSERT INTO holidays (day, name) VALUES
    ('2026-01-01', 'Новый год'),
    ('2026-01-02', 'Новый год'),
    ('2026-01-07', 'Рождество Христово'),
    ('2026-03-08', 'Международный женский день'),
    ('2026-03-21', 'Наурыз мейрамы'),
    ('2026-03-22', 'Наурыз мейрамы'),
    ('2026-03-23', 'Наурыз мейрамы'),
    ('2026-05-01', 'Праздник единства народа Казахстана'),
    ('2026-05-07', 'День защитника Отечества'),
    ('2026-05-09', 'День Победы'),
    ('2026-07-06', 'День столицы'),
    ('2026-08-30', 'День Конституции'),
    ('2026-10-25', 'День Республики'),
    ('2026-12-16', 'День Независимости'),
    ('2027-01-01', 'Новый год'),
    ('2027-01-02', 'Новый год'),
    ('2027-01-07', 'Рождество Христово'),
    ('2027-03-08', 'Международный женский день'),
    ('2027-03-21', 'Наурыз мейрамы'),
    ('2027-03-22', 'Наурыз мейрамы'),
    ('2027-03-23', 'Наурыз мейрамы'),
    ('2027-05-01', 'Праздник единства народа Казахстана'),
    ('2027-05-07', 'День защитника Отечества'),
    ('2027-05-09', 'День Победы'),
    ('2027-07-06', 'День столицы'),
    ('2027-08-30', 'День Конституции'),
    ('2027-10-25', 'День Республики'),
    ('2027-12-16', 'День Независимости');
