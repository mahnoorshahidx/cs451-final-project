# Savings history, target dates and trip deletion (UC7, UC8, UC12)

Backend only. All endpoints need `Authorization: Bearer <token>`. Errors are `{ "message": "..." }`.
Nothing existing was removed or renamed: the changes are one new endpoint, three new fields at
the end of the savings response, one new 400 error, and a fix so trips can always be deleted.

## Permissions

| Action | Owner | Editor | Viewer | No access |
|---|---|---|---|---|
| View the savings goal and projection (`GET /savings`) | ✅ | ✅ | ✅ | 404 |
| Filter the contribution history (UC7) | ✅ | ✅ | ✅ | 404 |
| Filter by a bank account | own accounts only | own accounts only | own accounts only | 404 |
| Create / update the goal (UC8 date rule applies) | ✅ | ✅ | 403 | 404 |
| Delete the trip (UC12) | ✅ | 403 | 403 | 404 |
| Not signed in | 401 ||||

Bank account privacy works as before (UC9): a contribution's `bankAccountId` is only shown to the
person who owns that bank account; everyone else gets `null`.

---

## UC7: contribution history with filters

### `GET /api/trips/{tripId}/savings/contributions`

All parameters are optional and can be combined. Leaving a parameter out (or empty) means "don't filter".

| Parameter | Format | Meaning |
|---|---|---|
| `from` | `2027-01-31` | contributions on or after this date |
| `to` | `2027-03-31` | contributions on or before this date |
| `bankAccountId` | number | contributions made from this bank account. **Must be one of your own accounts.** |

With no parameters, every contribution is listed, exactly like the `contributions` list from
`GET /api/trips/{tripId}/savings` (newest first). That endpoint is unchanged.

**Response 200**
```json
{
  "tripId": 10,
  "from": "2026-10-01",
  "to": "2026-11-30",
  "bankAccountId": null,
  "filteredCount": 2,
  "filteredTotal": 150.00,
  "contributions": [
    { "id": 7, "amount": 50.00, "date": "2026-11-20", "description": "Paycheck",
      "bankAccountId": 501, "addedByYou": true, "canDelete": true },
    { "id": 6, "amount": 100.00, "date": "2026-11-10", "description": "Saved",
      "bankAccountId": null, "addedByYou": false, "canDelete": true }
  ],
  "goalTargetAmount": 2000.00,
  "goalSavedAmount": 250.00,
  "goalRemainingAmount": 1750.00,
  "goalPercentFunded": 12,
  "goalStatus": "ON_TRACK",
  "projectedCompletionDate": "2028-07-12",
  "projectionStatus": "BEHIND_PACE",
  "message": null
}
```

- `filteredCount`, `filteredTotal` and `contributions` describe only the listed contributions.
- Everything starting with `goal`, plus `projectedCompletionDate` and `projectionStatus`, is for the
  **whole goal** and is the same whatever filters you use (it matches `GET /savings`).
- `message` is set when nothing is listed: `"No contributions match these filters"`,
  `"No contributions yet"`, or `"This trip has no savings goal yet"` (then the `goal…` fields are `null`).

**Errors**

| Status | When | Message |
|---|---|---|
| 400 | `from` or `to` isn't a real date | `from must be a date like 2027-01-31` |
| 400 | `from` is after `to` | `Start date (from) can't be after end date (to)` |
| 400 | `bankAccountId` isn't a number | `bankAccountId must be a number` |
| 404 | `bankAccountId` isn't one of **your** accounts (someone else's, or doesn't exist) | `Bank account not found` |
| 404 | you have no access to the trip (checked first, before the filters) | `Trip not found` |

Filtering by someone else's account gives the same 404 as an account that doesn't exist, so no one
can find out whether another member's account exists.

### Projected completion date (new fields on `GET /api/trips/{tripId}/savings`)

Three fields were added at the end of the savings response:

| Field | Meaning |
|---|---|
| `projectedCompletionDate` | when the goal will be funded at the recent pace; `null` unless the status is `ON_PACE`, `BEHIND_PACE` or `PROJECTED` |
| `projectedMonthlyRate` | recent savings per month, rounded for the trip's currency (JPY/KRW: no decimals); `null` without enough history |
| `projectionStatus` | see below |

How it's calculated (`SavingsProjection`):
1. Take the contributions dated in the **last 90 days**, up to today. Future-dated contributions are ignored.
2. You need **at least 2** of them, and the first one must be **at least 14 days ago**.
3. Daily pace = their total ÷ days since the first one.
4. Projected date = today + (amount still needed ÷ daily pace), **rounded up** to a whole day.

| `projectionStatus` | Meaning |
|---|---|
| `FUNDED` | the goal is already met (no date) |
| `ON_PACE` | the projected date is on or before the target date |
| `BEHIND_PACE` | the projected date is after the target date (also when the target date has passed) |
| `PROJECTED` | a date was projected but the goal has no target date to compare with. Can't happen today, because the target date is required, but it's handled safely. |
| `INSUFFICIENT_HISTORY` | fewer than 2 contributions in the last 90 days, or less than 14 days of history |
| `NO_RECENT_SAVINGS` | contributions exist, but none in the last 90 days (`projectedMonthlyRate` is 0) |
| `RATE_TOO_LOW` | at this pace the goal would take more than 100 years (no date) |

The money math uses `BigDecimal` in one exact step (no rounded daily rate), so tiny rates never
divide by zero, and the 100-year limit keeps dates valid. "Today" is the **server's date**.

The existing `status` field (`FUNDED`, `ON_TRACK`, `BEHIND`, `NO_PLAN`, `PAST_DUE`) is unchanged:
it compares your *planned* monthly amount with what's needed. `projectionStatus` looks at what was
*actually* saved.

---

## UC8: target date can't be in the past

`PUT /api/trips/{tripId}/savings` (request unchanged)

| Situation | Result |
|---|---|
| New goal with a date before today | **400** `Target date can't be in the past. Choose today or a later date.` |
| New goal with today or a later date | 200 |
| Existing goal, date **changed** to a date before today | **400** (same message), nothing is changed |
| Existing goal, date changed to today or later | 200 |
| Existing goal whose date **has already passed**, same date sent back while changing the amount, starting amount or monthly plan | 200. The date stays, and `status` stays `PAST_DUE` |
| `targetDate` missing | 400 `Target date is required` (validation, as before) |

"Today" is the server's date (`LocalDate.now()` on the backend). Contributions added without a date
still use the server's today, as before.

---

## UC12: deleting a trip

`DELETE /api/trips/{id}` → **204**. Owner only (403 for editors and viewers, 404 without access). Same contract as before.

**What was wrong:** deleting a trip that had expenses or reminder notifications failed with a
database error ("Cannot delete or update a parent row"), because those foreign keys had no
`ON DELETE CASCADE` and the backend didn't remove them.

**What happens now**, in one transaction:
1. The trip row is locked (`SELECT … FOR UPDATE`). Anything being added to the trip at the same
   moment (an expense, a contribution, a search result) waits for the delete.
2. Ownership is checked (after the lock, so it sees the latest data).
3. Deleted, in this order:
   - savings contributions
   - the savings goal
   - expenses
   - notifications
   - itinerary activities (including UC6 search results and their cost estimates)
   - the budget and its category allocations
   - members
   - invitations
   - the trip.

**Not deleted:** users, bank accounts (contributions made from them are removed, the accounts stay),
other trips and anything belonging to them.

If any step fails, the whole transaction is rolled back and nothing is deleted.

The database also cascades these rows once `database/updates/2026-11-27-trip-delete-cascade.sql` has
run. The backend deletes them itself as well, so deletion works on a database that hasn't been
updated yet.

**Concurrency** (checked on MySQL 8):

| Situation | Result |
|---|---|
| A member adds an expense while the trip is being deleted | The insert waits, then fails because the trip is gone. Nothing is left orphaned. |
| An expense insert is still in progress when the delete starts | The delete waits for it, then deletes that expense too. |
| Two deletes of the same trip at the same time | The first deletes it; the second waits, then gets 404. |

---

## Database migration (UC12)

File: `database/updates/2026-11-27-trip-delete-cascade.sql`. No columns or data change.

It makes these four foreign keys `ON DELETE CASCADE`:

| Column | Constraint |
|---|---|
| `expenses.trip_id` → `trips.id` | `fk_expenses_trip` |
| `notifications.trip_id` → `trips.id` | `fk_notifications_trip` |
| `savings_goals.trip_id` → `trips.id` | `fk_savings_goals_trip` |
| `transactions.savings_goal_id` → `savings_goals.id` | `fk_transactions_goal` |

`transactions.bank_account_id` and every foreign key to `users` are **not** changed.
`database/schema.sql` has the same four changes, for new databases.

**How to run it**
1. Stop the backend.
2. Back up the database.
3. If your database isn't called `trip_budget_planner` (for example Aiven's `defaultdb`), change the
   `USE` line at the top.
4. Run it:
   `mysql -u <user> -p < database/updates/2026-11-27-trip-delete-cascade.sql`,
   or open it in MySQL Workbench and run the whole script.
5. Check the last result: 4 rows, all `CASCADE`.
6. Start the backend.

**Safety**
- **Orphaned rows stop it before anything changes.** Step 1 counts rows pointing at a trip or goal that
  doesn't exist. If any count isn't 0, the script fails on purpose with
  `Table '…STOPPED_orphaned_rows_found_see_step_1' doesn't exist`, and no foreign key has been touched.
  Even with `--force`, every change is skipped while orphans exist. Delete those rows and run it again.
- **Works with Hibernate-made databases.** Foreign keys are found by column, not by name, so generated
  names like `FK8xyz…`, duplicate keys and missing keys are all handled.
- **Safe to run again.** MySQL commits each `ALTER TABLE` by itself, so the script can't be one
  transaction. Instead each step checks the current state first. If a run stops halfway, run it
  again and it finishes the rest.
- MySQL can't drop and re-add the same constraint name in one statement, so each key is fixed in
  two steps (drop the old keys, then add the cascade key). Between the two there's a moment without
  a key, which is another reason to run it with the backend stopped.

Tested on MySQL 8.0 with:
- the previous `schema.sql` plus data, run twice
- Hibernate-style names, a duplicate key and a missing key
- a half-finished run
- orphaned rows, both normally and with `--force`
- a fresh `schema.sql` (applied twice)
- deleting trips afterwards, confirming that other trips, users and bank accounts remain.

---

## Trying it locally

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"<your password>"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# UC7
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/trips/1/savings                                # new projection fields
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/api/trips/1/savings/contributions"                # no filters
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/api/trips/1/savings/contributions?from=2026-11-01&to=2026-11-30"
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/api/trips/1/savings/contributions?bankAccountId=1"
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/api/trips/1/savings/contributions?from=2026-12-01&to=2026-11-01"   # 400

# UC8
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' localhost:8080/api/trips/1/savings \
  -d '{"targetAmount":2000,"targetDate":"2020-01-01"}'                                                       # 400 (new or changed date)

# UC12: a trip with expenses and notifications can now be deleted
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE -H "Authorization: Bearer $TOKEN" localhost:8080/api/trips/1   # 204
```

**Known limitation (not changed here):** disconnecting a bank account that savings contributions
still use fails (`transactions.bank_account_id` has no cascade). That is on purpose, so contribution
history isn't lost silently, and it is outside UC12.
