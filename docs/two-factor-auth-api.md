# Two-factor sign-in API (UC1)

After the password checks out, a 6-digit code is sent and must be entered before a token is issued.
Codes are delivered by `LogVerificationCodeSender`, which writes them to the **backend console**
("Sign-in code for x@y.com: 123456"). Replace it with an email sender (a `@Primary` bean that
implements `VerificationCodeSender`) to send real emails.

## Settings (`application.properties`)

| Property | Default | |
|---|---|---|
| `app.auth.two-factor.enabled` (`TWO_FACTOR_ENABLED`) | `true` | `false` = sign-in returns the token straight away, as before |
| `app.auth.two-factor.code-minutes` | 10 | How long a code works |
| `app.auth.two-factor.max-attempts` | 5 | Wrong codes before the sign-in is closed |
| `app.auth.two-factor.resend-seconds` | 30 | Wait between resends |
| `app.auth.two-factor.max-sends` | 5 | Codes per sign-in, the first one included |
| `app.auth.two-factor.show-code` (`TWO_FACTOR_SHOW_CODE`) | `false` | Demo only: also returns the code as `devCode` so the sign-in screen shows it |

## Endpoints (no token needed)

### `POST /api/auth/login`
Body `{ "email", "password" }`. Wrong email or password: 401 as before.

Two-factor on:
```json
{ "twoFactorRequired": true, "challengeId": "5f0c…", "sentTo": "h****@example.com",
  "expiresInSeconds": 600, "resendAvailableInSeconds": 30, "devCode": null,
  "token": null, "name": null, "email": null }
```
Two-factor off: `{ "twoFactorRequired": false, "token": "…", "name": "…", "email": "…" }` (other fields null).

### `POST /api/auth/verify`
Body `{ "challengeId", "code" }` → `{ "token", "name", "email" }`.

| Code | When |
|---|---|
| 400 | Wrong code (message says how many tries are left), or the code isn't 6 digits |
| 410 | Expired, already used, too many wrong codes, replaced by a newer sign-in, or unknown. Sign in again |

### `POST /api/auth/resend`
Body `{ "challengeId" }` → same shape as the login challenge, with a new code (the old one stops working).
429 during the cooldown or after `max-sends`; 410 as above.

## Database
Run `database/updates/2026-11-28-login-challenges.sql` (or let Hibernate create `login_challenges`).
Only a BCrypt hash of each code is stored.
