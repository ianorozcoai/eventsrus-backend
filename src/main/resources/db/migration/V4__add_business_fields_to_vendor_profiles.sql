ALTER TABLE vendor_profiles ADD COLUMN business_name VARCHAR(255);
ALTER TABLE vendor_profiles ADD COLUMN business_type VARCHAR(20)
    CHECK (business_type IS NULL OR business_type IN
        ('CATERING', 'PHOTOGRAPHY', 'VENUE', 'ENTERTAINMENT', 'DECORATION', 'OTHER'));
