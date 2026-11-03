# Pathora Company Portal

A dedicated web application for Pathora companies — separate from both the React Native mobile
app and the `admin-web` platform-admin portal. It talks to the same Spring Boot backend and the
same database as both of those; no new backend, no duplicated application records.

This increment covers:

- Company login (COMPANY role only — platform ADMIN and INTERN accounts are rejected).
- A protected company layout (sidebar navigation + session handling).
- A dashboard (company profile summary, approval status, internship/template counts).
- Read-only internship listing (internship creation/editing stays mobile-first for now — see
  the Phase 0 architecture report).
- Pipeline template listing, creation, renaming, duplication, and archiving.
- A pipeline builder: add, rename, reorder (via up/down controls), and delete a template's
  ordered stages.

Not yet implemented (left for a later increment, per the current scope): assigning a template to
a specific internship's own pipeline, the candidate Kanban board, evaluations, and analytics.
The corresponding backend endpoints already exist (`/api/v1/company/ats/internships/{id}/pipeline/**`)
but have no UI here yet.

## Stack

React + TypeScript + Vite + React Router — no UI framework, matching `admin-web`'s own
conventions exactly. Plain CSS using Pathora's existing color/spacing tokens (ported from the
mobile app's design system).

## Prerequisites

- The Pathora backend running locally on port 8080 (see `backend/`).
- At least one company account, APPROVED by an admin, with a completed profile (register via
  the mobile app, approve via `admin-web`).
- Node.js 20+.

## Local development

```
npm install
npm run dev
```

Open the printed URL (default `http://localhost:5174` — a different port from `admin-web`'s
5173, so both can run side by side) and log in with a company account's email and password.

The dev server proxies any request starting with `/api` to `http://localhost:8080` (see
`vite.config.ts`), so the frontend only ever calls relative URLs and never needs CORS
configuration or a hardcoded backend host.

## Environment configuration

Copy `.env.example` to `.env` if you need to override anything (optional for local dev):

```
cp .env.example .env
```

`VITE_API_BASE_URL` is the only variable. Leave it empty for local development. **Never put a
backend secret in this file** — anything prefixed `VITE_` is bundled into the built JavaScript
and shipped to the browser in plain text.

## Session handling

The company user's JWT is stored in `sessionStorage` under the key `pathora_company_token` —
a distinct key from `admin-web`'s `pathora_admin_token`, so the two portals never share or
clobber each other's session even when both are open in the same browser. The token is cleared
on logout, on a 401 from any API call, on a failed membership check (a revoked or non-member
account), and naturally when the browser tab closes. It is never logged, written to a cookie,
or put in a URL.

## Authorization

- Login is rejected client-side for any non-COMPANY role before a token is ever stored.
- After login, the app resolves the user's company membership via
  `GET /company/ats/members/me` — a revoked or non-existent membership signs the user back out
  immediately with a clear message.
- OWNER-only actions (creating/renaming/archiving/duplicating templates, configuring stages,
  assigning a default) are hidden in the UI for a RECRUITER, and independently enforced by the
  backend (`CompanyAccessResolver.requireOwner`) regardless of what the UI shows — the frontend
  hiding a button is a UX nicety, never the actual security boundary.
- None of this weakens or duplicates backend security; every request still goes through the
  existing JWT resource-server authentication and the existing role/membership/approval checks.

## Scope

Deliberately limited to the pages listed above for this increment — no candidate board,
evaluations, analytics, or team management yet.
