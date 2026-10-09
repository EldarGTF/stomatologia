-- Имя и телефон клиента в разговоре: Telegram передаёт подтверждённый номер кнопкой «Поделиться контактом».
ALTER TABLE conversations ADD COLUMN client_name VARCHAR(100);
ALTER TABLE conversations ADD COLUMN client_phone VARCHAR(30);
