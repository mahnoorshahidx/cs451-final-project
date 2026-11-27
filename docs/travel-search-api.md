# Travel search API (UC6)

Search flights, hotels, restaurants and activities for a trip, add a result to the itinerary,
and optionally add its estimated cost to the budget. Backend only.

> **All results are demo data.** They come from `MockTravelSearchProvider`: realistic but invented
> names (ending in "(demo)"), placeholder airlines ("Demo Air") and estimated prices. Show the
> `notice` from the response, or check `GET /api/search/provider` and show a "Demo data" banner
> when `live` is `false`.

All endpoints need `Authorization: Bearer <token>`. Errors are `{ "message": "..." }`.

## Permissions

| Action | Owner | Editor | Viewer | No access |
|---|---|---|---|---|
| Search (any type), see `alreadyAdded` | ✅ | ✅ | ✅ | 404 |
| Add a result (with or without `updateBudget`) | ✅ | ✅ | 403 | 404 |
| Set `estimatedCost` on a manual activity | ✅ | ✅ | 403 | 404 |
| Not signed in | 401 ||||

## Prices: what they cover

Every result has `unitPrice`, `priceBasis`, `quantity`, `quantityDescription` and `estimatedTotal`.
**`estimatedTotal = unitPrice × quantity` is always for the whole group.**
That is the amount stored on the itinerary entry and added to the budget.

| Type | `priceBasis` | `quantity` |
|---|---|---|
| Flight | `PER_PERSON_ROUND_TRIP` (per person, round trip) | number of travelers |
| Hotel | `PER_ROOM_PER_NIGHT` (per room, per night) | rooms × nights. Rooms = travelers ÷ 2, rounded up |
| Restaurant | `PER_PERSON` (estimated meal per person) | number of travelers |
| Activity | `PER_PERSON` (ticket), or `PER_GROUP` for private guides | travelers, or 1 |

`priceBasisLabel` has readable text ("per room, per night").

**Currency:**
- All prices are in the **trip's currency** (`currency`), rounded the usual way for that currency. JPY and KRW have no decimals.
- `originalUnitPrice`, `originalTotal` and `originalCurrency` are the provider's own prices (USD for the demo).
- `exchangeRate` shows the rate used: `source` is `"live"` or `"fallback"`, and `notice` is set when demo rates were used. It is `null` when no conversion was needed.
- One rate snapshot is used for a whole search, and another for each add request.
- Rates can change between searching and adding, so the add response shows the exact price that was saved.

## Endpoints

### `GET /api/search/provider`
```json
{ "provider": "demo", "live": false, "types": ["FLIGHT","HOTEL","RESTAURANT","ACTIVITY"],
  "notice": "Demo data for development, not live prices or availability." }
```

### Search (any trip member)

Destination, travelers and currency always come from the trip. Dates default to the trip's dates
and must be inside them. All parameters are optional unless marked.

| Endpoint | Parameters |
|---|---|
| `GET /api/trips/{tripId}/search/flights` | **`origin`** (3-letter airport code, required), `departDate`, `returnDate` (return ≥ depart) |
| `GET /api/trips/{tripId}/search/hotels` | `checkIn`, `checkOut` (at least one night), `minRating` (0–5) |
| `GET /api/trips/{tripId}/search/restaurants` | `date`, `cuisine` (text), `priceLevel` (1–4), `minRating` |
| `GET /api/trips/{tripId}/search/activities` | `date`, `keyword` (text), `minRating` |

Every search also accepts:
- `maxPrice`: compared with **`unitPrice` in the trip's currency**. Must be greater than 0, with at most 2 decimals.
- `sort`: `price` (lowest unit price first) or `rating` (highest first).
- `limit`: 1–20, default 10.

Dates use `2027-06-01` format, and text filters are limited to 50 characters.

Example: `GET /api/trips/10/search/hotels?limit=1` for a JPY trip to Tokyo with 3 travelers, using the demo exchange rates:
```json
{
  "type": "HOTEL", "tripId": 10, "destination": "Tokyo", "currency": "JPY", "travelers": 3,
  "source": "demo", "live": false,
  "notice": "Demo data for development, not live prices or availability.",
  "exchangeRate": { "from": "USD", "to": "JPY", "rate": 150.000000, "rateDate": null, "source": "fallback",
                    "notice": "Live exchange rates are unavailable. This uses fixed demo rates, so it is only an estimate." },
  "results": [ {
    "itemId": "demo-hotel-tokyo-20270601-20270610-3-x-0",
    "type": "HOTEL", "name": "Saffron Residences Tokyo (demo)", "location": "Shinjuku, Tokyo", "rating": 4.0,
    "date": "2027-06-01", "endDate": "2027-06-10", "startTime": "15:00:00", "endTime": null,
    "returnStartTime": null, "returnEndTime": null,
    "priceBasis": "PER_ROOM_PER_NIGHT", "priceBasisLabel": "per room, per night",
    "unitPrice": 39188, "quantity": 18, "quantityDescription": "2 rooms x 9 nights", "estimatedTotal": 705384,
    "currency": "JPY", "originalUnitPrice": 261.25, "originalTotal": 4702.50, "originalCurrency": "USD",
    "budgetCategory": "Accommodation", "alreadyAdded": false, "source": "demo",
    "details": { "stars": "4", "nights": "9", "rooms": "2", "checkOut": "2027-06-10", "breakfastIncluded": "true" }
  } ]
}
```

Flight results also have `returnStartTime` and `returnEndTime`. Their `details` include
`airline`, `outboundFlight`, `returnFlight`, `from`, `to`, `stops`, `durationHours` and `cabin`.
A `null` end time means the flight lands the next day.

### `POST /api/trips/{tripId}/search/items` (owner or editor) → 201

```json
{ "itemId": "demo-hotel-tokyo-20270601-20270610-3-x-0", "updateBudget": true, "notes": "Late check-in" }
```

| Field | |
|---|---|
| `itemId` | **Required.** From a search result. The server looks the item up again and uses the provider's price and details, never the client's |
| `updateBudget` | Optional, **default `false`**. `true` also adds `estimatedTotal` to the budget |
| `startTime` | Optional `"19:30"`. Restaurants and activities only; the length stays the same |
| `notes` | Optional, up to 2000 characters, added under the generated notes |

**What gets created:**
- **Hotel:** one entry on the check-in date, titled "Check in: …", category `Accommodation`.
- **Restaurant / activity:** one entry, category `Food & Drink` or `Activity`.
- **Round-trip flight:** **two entries**, outbound on the departure date and return on the return date, both category `Transportation`.
  - The cost (and any budget amount) is stored **once**, on the outbound entry.
  - The return entry has `estimatedCost: null` and a note saying the price is included with the outbound flight.

Response:
```json
{
  "activities": [ { "id": 41, "tripId": 10, "activityDate": "2027-06-01", "title": "Check in: Saffron Residences Tokyo (demo)",
      "startTime": "15:00:00", "endTime": null, "category": "Accommodation", "location": "Shinjuku, Tokyo",
      "notes": "2 rooms x 9 nights, check out 2027-06-10. Estimated 705384 JPY (39188 per room, per night, 2 rooms x 9 nights). Demo data.\nLate check-in",
      "estimatedCost": 705384, "costCurrency": "JPY", "sourceType": "HOTEL", "sourceProvider": "demo",
      "sourceSegment": "MAIN", "budgetAmountAdded": 705384, "budgetAmountCurrency": "JPY" } ],
  "item": { "...": "the result as priced when added, with alreadyAdded: true" },
  "budgetChange": { "category": "Accommodation", "amountAdded": 705384, "currency": "JPY",
      "allocationBefore": 0, "allocationAfter": 705384, "totalBefore": 0, "totalAfter": 705384,
      "budgetCreated": true, "budgetVersion": 1, "conversion": null },
  "notice": "Demo data for development, not live prices or availability."
}
```

**Budget rules for `updateBudget=true`:**
- **Currency:** the amount is converted to the **budget's** currency if it differs from the trip's. `budgetChange.conversion` shows the rate, and `budgetAmountCurrency` stores which currency it was.
- **Category:** a matching category is reused if the budget already has one (for example "flight" or "Flights"). Otherwise the standard category is added: `Flights`, `Accommodation`, `Food` or `Activities`.
- **Total:** if the allocations would go over the total, the total grows to match. Otherwise the total stays the same.
- **No budget yet:** one is created in the trip's currency.
- **Unsupported budget currency** (for example MAD): **409**, and nothing is added (not even the activity). Retry with `updateBudget: false`.

**Errors:**

| Code | When |
|---|---|
| 400 | Bad input, a `startTime` for a flight or hotel, or the result **doesn't match the trip** (different destination, group size or dates). This also covers results from before the trip was edited; search again |
| 403 | Viewer |
| 404 | No access to the trip, or the `itemId` is unknown or malformed |
| 409 | **Already in the itinerary** (each result can be added once per trip), or an unsupported budget currency |

## Important behavior

- **Deleting an activity doesn't reduce the budget unless asked.** `DELETE /api/trips/{id}/activities/{activityId}?adjustBudget=true` takes the entry's `budgetAmountAdded` back out of its budget category (never below zero; the total drops with it but never below what is still allocated). Without the flag, the amount stays in the budget as before. Ask the user when an entry has `budgetAmountAdded`.
- **AI "replace existing" keeps search results.** `POST /activities/generate` and applying an AI option (UC5) with `replaceExisting: true` only remove AI and manual activities; flights, hotels and other search items stay, and the AI is told they're booked.
- **Trip currency:** once an itinerary entry has an `estimatedCost`, the trip's currency can't be changed (409), like with budgets, expenses and savings. Creating a budget through `updateBudget` also locks the currency.
- **Trip edits make old results stale:** changing the trip's travelers or dates after searching makes those results stale (400 when adding). Search again.

## Changes to existing endpoints (backward compatible)

**Activities** (`/api/trips/{id}/activities`):
- Responses have seven new fields, all `null` for existing manual and AI activities: `estimatedCost`, `costCurrency`, `sourceType`, `sourceProvider`, `sourceSegment`, `budgetAmountAdded` and `budgetAmountCurrency`.
- POST and PUT accept an optional `estimatedCost` for the whole group, in the trip's currency. It must be ≥ 0, with at most 2 decimals (0 for JPY and KRW).
- On PUT, **leaving `estimatedCost` out keeps the saved cost**, so the current editor doesn't erase it.

**Budget** (`/api/trips/{id}/budget`):
- Responses include `version`.
- PUT accepts an optional `version`. If someone (for example another member, or an `updateBudget` add) changed the budget since that version, the save is refused with **409** and the page should reload.
- Without `version`, saves work as before.
- All budget changes on a trip are serialized with a database lock, so simultaneous changes don't overwrite each other.

## Database

Run `database/updates/2026-11-26-itinerary-costs.sql` before starting the updated backend. It adds:
- the new nullable `itinerary_activities` columns, with checks and the unique key `(trip_id, source_provider, source_item_id, source_segment)`
- `trip_budgets.version`
