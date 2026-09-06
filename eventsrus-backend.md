# EventsRUs Backend REST API
**Project Name:** `eventsrus-backend` | **Stack:** Java Spring Boot + PostgreSQL (Supabase)[cite: 5]  
**Repository:** `https://github.com/ianorozcoai/eventsrus-backend.git`

## Overview
Core engine for data persistence, auth, ticketing, payments, and event logistics.[cite: 5]

## Specs
- **Framework:** Spring Boot (Spring Web, Spring Security, Spring Data JPA)[cite: 5]
- **Repository:** `https://github.com/ianorozcoai/eventsrus-backend.git`
- **Database:** PostgreSQL hosted on Supabase (with Flyway / Liquibase migrations)[cite: 5]
- **Auth:** JWT Tokens[cite: 5]
- **Modules:** `/api/v1/auth`, `/api/v1/events`, `/api/v1/tickets`, `/api/v1/users`[cite: 5]

## Tooling
- **Supabase MCP:** registered at project scope (`.mcp.json`, HTTP transport, `https://mcp.supabase.com/mcp`) with `docs, account, database, debugging, development, functions, branching` features enabled. Lets agents inspect/manage the Supabase project (schema, config, docs) directly. Requires one-time OAuth authentication via `claude /mcp` in a terminal.