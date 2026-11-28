# Shared trips API (UC9)

How to share a trip, manage members and handle invitations. Backend only; no emails or texts are sent.
Invitations appear in the app (`GET /api/invitations`) or are shared as a link by the trip owner.

All endpoints need `Authorization: Bearer <token>` except the invitation preview.
Errors always look like `{ "message": "..." }`.

## Roles

| Role | Who | Can do |
|---|---|---|
| `OWNER` | The user who created the trip (always exactly one) | Everything, including delete the trip and manage members |
| `EDITOR` | Invited member | View and change the trip, budget, expenses, itinerary and savings goal. Add contributions and delete **their own** contributions |
| `VIEWER` | Invited member | Read only |

## Permission matrix

| Feature | Owner | Editor | Viewer | No access |
|---|---|---|---|---|
| View trip, budget, expenses, summary, comparison, itinerary, savings goal | ✅ | ✅ | ✅ | 404 |
| Edit trip details (currency guard still applies) | ✅ | ✅ | 403 | 404 |
| Save budget; add/edit/delete expenses and activities; AI itinerary; save savings goal; add contributions | ✅ | ✅ | 403 | 404 |
| Delete a savings contribution | any | only their own | 403 | 404 |
| Delete trip | ✅ | 403 | 403 | 404 |
| View member list | ✅ with emails | ✅ no emails | ✅ no emails | 404 |
| Invite, resend, revoke, change roles, remove others | ✅ | 403 | 403 | 404 |
| Leave the trip | — | ✅ | ✅ | 404 |
| Bank accounts, dashboard, `/api/budget-overview`, notifications | Always only your own (shared trips are never included) ||||

Status codes: `401` not signed in · `403` role too low · `404` no access or not found · `409` duplicate/conflict · `410` invitation no longer usable · `429` resend cooldown.

## Changes to existing responses

`TripResponse` (all `/api/trips` endpoints) has two new fields:

```json
{ "id": 10, "name": "Tokyo Trip", "destination": "Tokyo", "startDate": "2027-06-01", "endDate": "2027-06-10",
  "numberOfTravelers": 3, "currency": "USD", "role": "EDITOR", "ownerName": "Olivia Owner" }
```

`GET /api/trips` still returns only trips you own (`role` is always `OWNER`). Shared trips are in `GET /api/trips/shared`.

Savings contributions (`GET /api/trips/{id}/savings` and friends) have two new fields, and `bankAccountId` is now private:

```json
{ "id": 2, "amount": 100.00, "date": "2026-11-01", "description": "Saved",
  "bankAccountId": null, "addedByYou": false, "canDelete": false }
```

- `bankAccountId` is only shown to the person who owns that bank account; everyone else gets `null`.
- Show a delete button only when `canDelete` is `true`.

## Trip endpoints

### `GET /api/trips/shared`
Trips other people shared with you, newest first. Response: a list of `TripResponse` with your `role`.

### `GET /api/trips/{tripId}/members` (any member)
```json
[
  { "userId": 1, "name": "Olivia Owner", "email": "owner@example.com", "role": "OWNER", "joinedAt": null, "you": true },
  { "userId": 2, "name": "Eddie Editor", "email": "editor@example.com", "role": "EDITOR", "joinedAt": "2026-11-24T18:00:00Z", "you": false }
]
```
`email` is `null` for everyone unless you are the owner.

### `PUT /api/trips/{tripId}/members/{userId}` (owner)
Body: `{ "role": "VIEWER" }` (`EDITOR` or `VIEWER`). Returns the updated member.
409 if `userId` is the owner. 404 if that user isn't a member.

### `DELETE /api/trips/{tripId}/members/{userId}` → 204
Owner removes a member, or a member uses their own `userId` to leave. 409 for the owner.

### `POST /api/trips/{tripId}/invitations` (owner) → 201
Email invitation: `{ "email": "friend@example.com", "role": "EDITOR" }`
Shareable link (single use): `{ "role": "VIEWER" }`

```json
{ "id": 70, "type": "EMAIL", "email": "friend@example.com", "role": "EDITOR", "status": "PENDING",
  "createdAt": "2026-11-24T18:00:00Z", "lastSentAt": "2026-11-24T18:00:00Z",
  "expiresAt": "2026-12-01T18:00:00Z", "resendAvailableAt": "2026-11-24T18:10:00Z",
  "token": "q3Xh...43 characters...", "invitePath": "/invite/q3Xh..." }
```

- **`token` is only returned here and on resend.** Only a hash is stored, so it can't be shown again.
- The frontend builds the link as `window.location.origin + invitePath` (add an `/invite/:token` route).
- Emails are trimmed and lowercased, the same way as sign-up.
- 409: you invited yourself, an existing member, or an email that already has a pending invitation (resend that one instead).

### `GET /api/trips/{tripId}/invitations` (owner)
Pending invitations, same shape as above with `token` and `invitePath` set to `null`.

### `POST /api/trips/{tripId}/invitations/{id}/resend` (owner)
New token (the old link stops working) and a new 7-day expiry. Works for `PENDING` and `EXPIRED` invitations.
429 within 10 minutes of the last send:
```json
{ "message": "This invitation was sent recently. You can resend it in 6 minute(s).", "retryAfterSeconds": 360 }
```
The response also has a `Retry-After` header.

### `DELETE /api/trips/{tripId}/invitations/{id}` (owner) → 204
Revokes it. 409 if it was already accepted or declined.

## Invitation endpoints (invited person)

### `GET /api/invitations`
Your pending email invitations:
```json
[ { "id": 70, "tripId": 10, "tripName": "Tokyo Trip", "destination": "Tokyo", "startDate": "2027-06-01",
    "endDate": "2027-06-10", "role": "EDITOR", "invitedBy": "Olivia Owner", "expiresAt": "2026-12-01T18:00:00Z" } ]
```

### `POST /api/invitations/{id}/accept` and `POST /api/invitations/{id}/decline`
For invitations from the list above. Accept returns:
```json
{ "tripId": 10, "tripName": "Tokyo Trip", "role": "EDITOR" }
```
Decline returns 204. 404 if the invitation isn't yours.

### `POST /api/invitations/accept`
Accept from a link. Body: `{ "token": "q3Xh..." }`. Same response as above.
- Links can be used by any signed-in user, once.
- Email invitations opened by a different account get 403 ("sign in with that account").

### `GET /api/invitations/preview/{token}` (public, no login)
For the invitation page before sign-in. Intentionally minimal (no dates, emails, IDs or money):
```json
{ "tripName": "Tokyo Trip", "destination": "Tokyo", "invitedBy": "Olivia", "role": "EDITOR",
  "expiresAt": "2026-12-01T18:00:00Z", "emailInvite": false }
```

### Decline invitation by token

`POST /api/invitations/decline`

Authentication required.

Request body:

```json
{
  "token": "invitation-token"
}
```

Success: `204 No Content`

Marks a valid pending invitation as DECLINED without
creating a trip membership.

Email invitations may only be declined by the intended
recipient. Declining a shareable invitation consumes
the invitation link.

Possible errors:

- 400: Invalid request body
- 401: Authentication required
- 403: Incorrect recipient account
- 404: Invalid invitation token
- 409: Trip owner cannot decline their own invitation
- 410: Invitation is no longer available


### Invitation errors
| Code | When | Example message |
|---|---|---|
| 404 | Unknown token (or trip deleted) | "This invitation link is not valid. The trip may have been deleted." |
| 409 | Already a member, or you own the trip | "You're already a member of this trip." |
| 410 | Expired | "This invitation has expired. Ask the trip owner to send a new one." |
| 410 | Already used (single use) | "This invitation has already been used." |
| 410 | Revoked / declined | "This invitation was cancelled by the trip owner." |

## Database

New tables `trip_members` and `trip_invitations`, and a new `transactions.created_by_user_id` column.
See `database/updates/2026-11-24-trip-sharing.sql` (run it before starting the updated backend).
