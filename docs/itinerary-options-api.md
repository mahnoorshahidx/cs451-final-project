# AI itinerary options API (UC5)

The AI makes **three options** from the same questionnaire answers (Budget-friendly, Balanced,
Treat yourself), the user compares them, and applies one. Options are **drafts**: nothing changes
in the itinerary until one is applied. The three are planned in parallel, so it takes about as long
as one itinerary. If one style fails, the others are still returned; 503 only when none worked.

Permissions: owner or editor (viewer 403, no access 404), like `POST /activities/generate`.

| Endpoint | |
|---|---|
| `POST /api/trips/{tripId}/itinerary-options` | Body: the same as `POST /activities/generate` (its `travelStyle` is replaced per option). Replaces the trip's older drafts. → 201, list of options |
| `GET /api/trips/{tripId}/itinerary-options` | The current drafts (empty list when none) |
| `POST /api/trips/{tripId}/itinerary-options/{optionId}/apply?replaceExisting=true` | Copies the option's activities into the itinerary and deletes all drafts. `replaceExisting` (default `false`) first removes AI and manual activities on the option's days. → 201, the new activities. 404 when the option is gone |
| `DELETE /api/trips/{tripId}/itinerary-options` | Throws the drafts away. → 204 |

Option:
```json
{ "id": 12, "style": "balanced", "label": "Balanced",
  "summary": "The big sights in the morning, a sit-down dinner each night.",
  "startDate": "2027-06-01", "endDate": "2027-06-05",
  "totalCost": 604.00, "currency": "USD", "activityCount": 18,
  "costByCategory": { "Food": 212.00, "Sightseeing": 316.00, "Transport": 76.00 },
  "createdAt": "2027-05-01T14:03:22Z",
  "activities": [ { "activityDate": "2027-06-01", "startTime": "09:00:00", "endTime": "10:00:00",
                    "title": "…", "category": "Food", "location": "…", "notes": "… (AI suggestion)",
                    "estimatedCost": 16.00, "costCurrency": "USD" } ] }
```
`totalCost` and the category costs are for the whole group, in the trip's currency (`null` when the AI gave no costs).

## Also changed in AI generation

- **Items added through travel search are kept** when `replaceExisting` is true, for both
  `POST /activities/generate` and applying an option. Only AI and manual activities are removed.
- The AI is told about search items already booked on the days it plans, so it plans around them.
- The AI reply now includes a one-line `summary` (used for options).

## Database
Run `database/updates/2026-11-28-itinerary-options.sql` (or let Hibernate create the two tables).
Deleting a trip deletes its drafts.
