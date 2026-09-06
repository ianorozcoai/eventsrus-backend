-- Lets a planner flag which of a vendor's packages a quotation request is
-- about - and, per this change, more than one (same multi-select concept as
-- vendor_operating_areas/vendor_catered_event_types). A simple join table
-- rather than adding a single nullable package_id column, since the whole
-- point is supporting more than one package per quotation.
CREATE TABLE quotation_packages (
    quotation_id      BIGINT NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
    vendor_package_id BIGINT NOT NULL REFERENCES vendor_packages(id) ON DELETE CASCADE,
    PRIMARY KEY (quotation_id, vendor_package_id)
);
