-- Служебные сообщения TOOL: вызовы инструментов ИИ-менеджера и их результаты. Нужны, чтобы модель
-- в следующих ходах видела, что ей вернула система; клиенту и в CRM не показываются.
ALTER TABLE chat_messages DROP CONSTRAINT chat_messages_role_check;
ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_role_check
    CHECK (role IN ('USER', 'ASSISTANT', 'OPERATOR', 'TOOL'));
