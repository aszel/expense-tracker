--liquibase formatted sql
--changeset expensetracker:3
ALTER TABLE expenses ADD COLUMN booking_type VARCHAR(16) NOT NULL DEFAULT 'EXPENSE';
ALTER TABLE expenses ADD CONSTRAINT expenses_booking_type_check CHECK (booking_type IN ('INCOME', 'EXPENSE'));
