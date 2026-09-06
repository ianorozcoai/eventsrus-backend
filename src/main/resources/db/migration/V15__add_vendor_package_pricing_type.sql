ALTER TABLE vendor_packages ADD COLUMN pricing_type VARCHAR(20) NOT NULL DEFAULT 'FIXED';
ALTER TABLE vendor_packages ADD COLUMN min_price NUMERIC(12,2);
ALTER TABLE vendor_packages ADD COLUMN max_price NUMERIC(12,2);
