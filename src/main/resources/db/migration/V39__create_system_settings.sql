-- Business-rule numbers that used to be application.properties @Value
-- defaults (vendor trial length, subscription warning window, referral
-- commission, coordinator daily question limit) are now DB-backed so an
-- admin can change them from the admin module without a redeploy. See
-- SystemSettingKey/SystemSettingService.
CREATE TABLE system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO system_settings (setting_key, setting_value) VALUES
    ('vendor-trial-days', '180'),
    ('subscription-warning-days', '30'),
    ('referral-commission-amount', '1500'),
    ('coordinator-daily-question-limit', '20');
