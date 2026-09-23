ALTER TABLE vendor_subscriptions ADD COLUMN payment_screenshot_key VARCHAR(255);
ALTER TABLE vendor_subscriptions ADD COLUMN payment_screenshot_uploaded_at TIMESTAMPTZ;
