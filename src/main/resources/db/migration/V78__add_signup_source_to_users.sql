-- Where an account first signed up from: WEB, ANDROID, IOS, MOBILE (a mobile app
-- build that did not say which phone) or UNKNOWN. NULL = created before this
-- column existed. Filled once, at account creation, from the X-Client-Platform
-- header (or the User-Agent when the header is missing). Informational only:
-- any client can claim anything, so it helps spot odd sign-ups, it is not a
-- security control.
ALTER TABLE users ADD COLUMN signup_source VARCHAR(20);
