-- Онлайн-запись с сайта: личная ссылка клиента на запись и подтверждение записи регистратором
ALTER TABLE leads ADD COLUMN public_token VARCHAR(64) UNIQUE;
ALTER TABLE leads ADD COLUMN confirmed_at TIMESTAMP;

-- Записи, оформленные регистратором по заявке, подтверждены в момент записи
UPDATE leads SET confirmed_at = updated_at WHERE status = 'BOOKED';
