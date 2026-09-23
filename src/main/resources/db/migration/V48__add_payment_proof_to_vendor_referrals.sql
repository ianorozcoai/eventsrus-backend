ALTER TABLE vendor_referrals ADD COLUMN payment_remarks TEXT;
ALTER TABLE vendor_referrals ADD COLUMN payment_proof_key VARCHAR(255);
ALTER TABLE vendor_referrals ADD COLUMN payment_proof_uploaded_at TIMESTAMPTZ;
