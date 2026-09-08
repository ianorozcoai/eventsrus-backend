-- A support ticket message can carry one optional screenshot/attachment -
-- private (like the vendor's own ID card/selfie), so this stores the S3
-- object key, not a public URL; the real URL is a presigned one generated
-- on read (see SupportTicketService).
ALTER TABLE support_ticket_messages ADD COLUMN attachment_key VARCHAR(500);
