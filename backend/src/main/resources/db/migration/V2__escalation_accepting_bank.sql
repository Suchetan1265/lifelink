-- Records which blood bank took over an escalated request, so only that bank
-- can fulfill it and the hospital can see who is covering the request.

ALTER TABLE requests ADD COLUMN accepted_bank_id BIGINT REFERENCES blood_banks(id);

CREATE INDEX idx_requests_accepted_bank ON requests(accepted_bank_id);
