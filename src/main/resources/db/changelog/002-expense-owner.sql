--liquibase formatted sql
--changeset expensetracker:2
ALTER TABLE expenses ADD COLUMN owner_subject TEXT;
CREATE INDEX expenses_owner_subject_id_idx ON expenses (owner_subject, id);
