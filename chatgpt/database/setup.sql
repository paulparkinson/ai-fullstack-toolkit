-- Run as FINANCIAL in the OCI FinancialDB schema. This creates an isolated
-- example table; it does not change existing application tables.
CREATE TABLE chatgpt_notes (
  note_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  note_text VARCHAR2(500 CHAR) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

