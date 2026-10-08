-- Переход на тенге: цены услуг и уже выставленные суммы пересчитываются по одному коэффициенту,
-- чтобы счета, оплаты и остатки по ним остались согласованными.

UPDATE services SET price = price * 5;
UPDATE invoices SET amount = amount * 5;
UPDATE payments SET amount = amount * 5;
