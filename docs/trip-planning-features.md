# Trip planning features

How trip creation, recommendations, editing, and the Budget & Savings page fit together, and where the code lives.

## Page map

| Page | Route | What it's for |
|---|---|---|
| Create Trip | `/create-trip` | Step-by-step wizard for **new** trips, with date and budget recommendations |
| My Trips | `/trips` | List of trips. Each card links to **Budget** and **Edit details** |
| Edit Trip | `/trips/:tripId/edit` | Edit one trip. `?tab=details` (name, destination, dates, travelers, delete) or `?tab=budget` (total, currency, category caps) |
| Budget & Savings | `/budget`, `/budget/:tripId` | What each trip costs and how much has been saved toward it. `/budget` opens the next upcoming trip |

> **Note:** Budget and Savings share one page for now, because each trip's savings goal is based on its budget. Savings is planned to move to its own page later (for example, all contributions across trips, filtered by bank account for UC7). The `Savings` sidebar item was removed until then.

---

## 1. Create Trip wizard

`frontend/src/pages/CreateTrip.jsx`, `frontend/src/styles/CreateTrip.css`

| Step | What happens |
|---|---|
| 1. Destination | Free text, or one of the "Popular right now" chips |
| 2. Travel timing | **I have set dates**, or let the app pick: **Recommend the best time** / **Find the cheapest time** take a travel window and a number of nights, then show recommended dates with a reason |
| 3. Travelers | Number of travelers and an optional trip name |
| 4. Trade-offs | Budget hotels, shared rooms, low-cost meals, long layovers, etc. These lower the budget estimate |
| 5. Total budget | Recommended total with Lean / Recommended / Comfortable presets and a per-category breakdown. Changing the currency recalculates it. Choose **Use the recommended split** or **I'll split it myself** |
| 6. Category caps | Edit each category (prefilled, or empty for a manual split) with a live breakdown on the right. It won't save while the caps add up to more than the total |

Saving creates the trip and its budget, then opens the trip on the Budget & Savings page.

## 2. Recommendation engine

`backend/.../recommendation/`

| File | What it does |
|---|---|
| `RecommendationController.java` | `POST /api/recommendations/timing` and `POST /api/recommendations/budget` (login required, nothing is saved) |
| `RecommendationProvider.java` | The interface the AI will implement later |
| `RuleBasedRecommendationProvider.java` | Today's default: fixed rules, no external calls |
| `DestinationProfiles.java` | Seasons and cost levels for Japan, Portugal, Colorado, Morocco, Paris, London and New York, plus an average fallback |
| `dto/*.java` | Request and response records |

- **Timing:** picks dates in the destination's best season (or low season for "cheapest", preferring Tuesday or Wednesday departures).
- **Budget:** estimates flights, accommodation, food, local transport and activities from travelers, nights, season and trade-offs, leaving about 10% unallocated as a buffer.
- Non-USD currencies use fixed demo exchange rates.

**Swapping in the AI later:** create `AiRecommendationProvider implements RecommendationProvider` in the same package, annotated `@Service @Primary`. Spring injects it instead of the rule-based one, and the controller and frontend don't change. Return `"ai"` as `source`, and keep the rule-based provider as the fallback for when the AI call fails.

Frontend helpers: `frontend/src/lib/budgetSuggestions.js` fits recommended caps to the user's total (with a local fallback if the recommendation can't load). `frontend/src/lib/budgetCategories.js` holds category icons and descriptions.

## 3. Edit Trip page

`frontend/src/pages/EditTrip.jsx`, `frontend/src/styles/EditTrip.css`

- **Trip details tab:** name, destination, dates, travelers, and **Delete trip**.
- **Budget tab:** total, currency, and an editable cap per category with a live breakdown (planned, unallocated buffer, or over budget). **✦ Suggest caps** fills the caps from the recommendation engine, scaled to the total. Nothing saves until **Save budget**.
- **Switch trip** (top right) jumps between trips.
- An orange dot on a tab means unsaved changes, and the page asks before you leave with unsaved changes.

## 4. Budget & Savings page

`frontend/src/pages/BudgetDashboard.jsx`, `frontend/src/styles/BudgetDashboard.css`

- **Totals for upcoming trips:** amount budgeted, saved so far, still to save, and how many trips are fully funded.
- **Your trips (left):** every upcoming trip with a countdown and savings progress bar. Past trips are behind a toggle.
- **Selected trip (right):**
  - **Savings goal:** "$850 of $2,600 saved", a status (Fully funded / On track / Behind plan / No monthly plan / Target date passed), and a progress bar with milestones showing which categories savings already cover.
  - **Pace:** what to save per month and per week to reach the target date, next to the user's own monthly plan.
  - **+ Add savings** and **Edit goal** open small forms on the card. Trips without a goal get **Set a savings goal**, with the target prefilled from the budget.
  - **Budget envelopes:** donut chart of the category caps. **Adjust caps** opens the Edit Trip Budget tab.
  - **Savings history:** every contribution, removable with ×.

### Savings backend

`backend/.../savings/`, using the existing `savings_goals` and `transactions` tables (no schema changes).

| Method | Path | What it does |
|---|---|---|
| GET | `/api/budget-overview` | Every trip with its budget total and savings progress |
| GET | `/api/trips/{id}/savings` | The trip's goal, progress and contributions (404 if none) |
| PUT | `/api/trips/{id}/savings` | Create or update the goal: `{ targetAmount, targetDate, initialSavedAmount?, plannedMonthlyContribution? }` |
| POST | `/api/trips/{id}/savings/contributions` | Log savings: `{ amount, date?, description?, bankAccountId? }` |
| DELETE | `/api/trips/{id}/savings/contributions/{cid}` | Remove a contribution |

Contributions are simulated, not real bank transfers. A linked `bankAccountId` must belong to the same user. `TripService.deleteTrip` removes a trip's savings goal and contributions first, because the database doesn't cascade them.

---

## Known gaps

- **Timing preference and trade-offs aren't stored.** The wizard sends `timingPreference` and `tradeOffs`, but the `trips` table has no columns for them yet, so the backend ignores them.
- **Flexible dates still save a date range.** "Recommend the best time" and "Find the cheapest time" save the recommended dates as the trip's start and end dates, because `start_date` and `end_date` are required.
- **"Spent vs. caps" isn't built.** It needs an expenses API; the `expenses` table exists but nothing reads or writes it yet.
- **Exchange rates are fixed demo values.**

## Try it

1. Create a trip: Kyoto, Japan → **Recommend the best time** → a window like Nov 2026 – Jun 2027, 6 nights. Dates appear with a reason.
2. Switch to **Find the cheapest time**: it picks a low-season Tuesday or Wednesday departure.
3. On the budget step, change the currency and the estimate recalculates. Pick **I'll split it myself** and fill in a few categories.
4. Save. You land on the Budget & Savings page for that trip.
5. **Set a savings goal**, then **+ Add savings**. The progress bar, milestones and trip list update.
6. On My Trips, **Edit details** opens the Edit Trip page. Deleting a trip that has savings works.