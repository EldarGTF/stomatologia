-- Откуда пришла запись на приём: регистратура, личный кабинет пациента, сайт или мессенджер.
ALTER TABLE appointments
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'REGISTRY'
        CHECK (source IN ('REGISTRY', 'PATIENT_ACCOUNT', 'WEBSITE', 'MESSENGER'));

UPDATE appointments a
SET source = 'PATIENT_ACCOUNT'
FROM users u
WHERE a.created_by = u.id
  AND u.role = 'PATIENT';

-- Заявки (лиды): обращения из мессенджеров, с сайта и по телефону до записи на приём.
CREATE TABLE leads (
    id              BIGSERIAL PRIMARY KEY,
    source          VARCHAR(20)  NOT NULL CHECK (source IN ('TELEGRAM', 'WHATSAPP', 'WEBSITE', 'PHONE')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'NEEDS_OPERATOR', 'BOOKED', 'REJECTED')),
    name            VARCHAR(100) NOT NULL,
    phone           VARCHAR(30),
    service_id      BIGINT REFERENCES services (id) ON DELETE SET NULL,
    doctor_id       BIGINT REFERENCES doctors (id) ON DELETE SET NULL,
    preferred_start TIMESTAMP,
    preferred_text  VARCHAR(200),
    summary         TEXT,
    consent_at      TIMESTAMP,
    patient_id      BIGINT REFERENCES patients (id) ON DELETE SET NULL,
    appointment_id  BIGINT REFERENCES appointments (id) ON DELETE SET NULL,
    assigned_to     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    reject_reason   VARCHAR(300),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_leads_status ON leads (status);
CREATE INDEX idx_leads_created ON leads (created_at);

-- Переписка с клиентом в мессенджере или чате сайта.
CREATE TABLE conversations (
    id               BIGSERIAL PRIMARY KEY,
    channel          VARCHAR(20)  NOT NULL CHECK (channel IN ('TELEGRAM', 'WHATSAPP', 'WEB_CHAT')),
    external_chat_id VARCHAR(100) NOT NULL,
    lead_id          BIGINT REFERENCES leads (id) ON DELETE SET NULL,
    mode             VARCHAR(20)  NOT NULL DEFAULT 'AI' CHECK (mode IN ('AI', 'OPERATOR', 'CLOSED')),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    last_message_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- В одном чате одновременно открыт только один разговор.
CREATE UNIQUE INDEX uq_conversations_open ON conversations (channel, external_chat_id) WHERE mode <> 'CLOSED';
CREATE INDEX idx_conversations_lead ON conversations (lead_id);

CREATE TABLE chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'OPERATOR')),
    text            TEXT        NOT NULL,
    author_id       BIGINT REFERENCES users (id) ON DELETE SET NULL,
    sent_at         TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, sent_at);
