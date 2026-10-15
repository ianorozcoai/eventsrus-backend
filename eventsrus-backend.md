# EventsRUs Backend REST API
**Project:** `eventsrus-backend` | **Stack:** Java Spring Boot, PostgreSQL on Supabase, Flyway | **Repo:** `github.com/ianorozcoai/eventsrus-backend`
**Parent doc:** `claude.md` (project root). Coding conventions: `agents/cto/sub_agents/developer.md`. Product workflow: `agents/cto/cto.md`.

## Role
The single source of truth for data and rules. Both `eventsrus-ui` (mobile) and `eventsrus-web` call it.

## Specs
- Spring Web, Spring Security (JWT; subject = user email), Spring Data JPA, Flyway migrations in `src/main/resources/db/migration` (`V<n>__description.sql`, currently up to about V33).
- File storage through `S3UploadService` (Cloudflare R2-style public CDN at `cdn.eventsrus.app`; private files via presigned URLs).
- Dockerfile present. Config in `application.properties`, with a `supabase` profile.

## Production
Railway, `production` environment: backend at https://api.eventsrus.app, with a Railway Postgres service (volume attached). The `supabase` profile and Supabase MCP in this repo appear to be a local/older setup; production is on Railway Postgres (confirmed from Ian's Railway dashboard, 2026-10-16).

## API areas (all under `/api/v1`)
`auth`, `users`, `events`, `events/{id}/coordinator`, `notifications`, `recommendations`, `vendors` (directory), `vendors/me/*` (dashboard, leads, packages, gallery, settings, payment-methods, legal-documents, social links, referrals, **subscription**, **google-calendar**), `users/me/device-tokens` (push), `webhooks/paypal`, and `admin/*` (vendors, planners, subscription payments, SMS, reviews, support tickets, settings, referrals, dashboard).

## Integrations
| Integration | Notes |
|---|---|
| PayPal subscriptions | `PayPalSubscriptionClient`, `PayPalWebhookService`; `VendorSubscriptionService` is the main logic. |
| GCash (manual) | Screenshot upload then admin review (`AdminSubscriptionPaymentController`). |
| Google sign-in and Calendar | OAuth clients in env vars (`GOOGLE_*`); refresh tokens encrypted at rest. |
| Firebase push | Service account file locally (git-ignored). |
| Semaphore SMS, Anthropic API | Used by `SmsService` and the AI suggestion helper. |

## Known gaps relevant to Google Play
No account-deletion endpoint, no user report/block endpoints. See `docs/play-audit.md`.

## Tooling
Supabase MCP is configured in the root `.mcp.json`.
