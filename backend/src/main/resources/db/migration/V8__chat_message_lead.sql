-- Сообщение принадлежит заявке, в рамках которой написано: у одного разговора (устройства, чата Telegram)
-- со временем бывает несколько заявок, и у каждой своя переписка.
ALTER TABLE chat_messages ADD COLUMN lead_id BIGINT REFERENCES leads (id);
CREATE INDEX idx_chat_messages_lead ON chat_messages (lead_id);

UPDATE chat_messages m
SET lead_id = c.lead_id
FROM conversations c
WHERE c.id = m.conversation_id;
