# Use Case Specifications

## Use Case 1 - Register & Login

| **Use Case Title** | Register & Login |
|---|---|
| **Actors** | Web User |
| **Preconditions** | Web User on the Login page |
| **Description** | This use case begins when a new user opens the website and wants to create an account. The user enters the required registration information, and the system validates the information and creates the account. After successful registration the user can log into the application using their account credentials. |

### Basic Flow

1. User opens up the website.
2. User clicks on "Create account."
3. The application displays the registration form that includes name, email, password, phone number, date-of-birth, and address.
4. The user enters the information on the registration form.
5. The system validates the information entered.
6. The system creates the user's account.
7. System confirms successful registration.
8. User logs into the application.
9. The system authenticates the user.
10. The system takes the user to the homepage/dashboard.

### Alternate Flow

1. **User enters invalid information**
   1. The app detects that the user entered missing or invalid information.
   2. The app displays an error message.
   3. The user corrects the information and submits again.

2. **Account is already created**
   1. The app detects an existing account with the registered email.
   2. The application displays this information to the user.
   3. The user either logs in with a registered email or uses a different email.

### Open Issues

1. What password requirements should we enforce?
2. Should we enforce 2-factor authentication?

---

## Use Case 2 - Connect Bank Account

| **Use Case Title** | Connect Bank Account |
|---|---|
| **Actors** | Normal user |
| **Preconditions** | 1. Two-factor authentication must occur after valid credentials are entered.<br>2. User must be successfully logged in.<br>3. User must have at least either a checking or savings account. |
| **Description** | Normal user enters bank information to connect their financial information to the app. |

### Basic Flow

1. User is presented with a page to enter bank information (checking or savings, routing number, account number).
   - User clicks "Connect Account."
2. System validates information:
   1. Are all fields filled?
   2. Does the routing and/or account number have the expected format?
3. If something is wrong, display the appropriate error.
4. If everything is valid, simulate successful bank verification.
5. Save the bank account to the user's profile.
6. Proceed to the next page → Travel trip information.

### Alternate Flow

1. **Incomplete Information**
   1. User leaves one or more required fields blank.
   2. User submits the form.
   3. System identifies the missing field(s).
   4. System displays an error message: "Incomplete field [X]."
   5. System does not proceed to the next page.
   6. User enters the missing information.
   7. System revalidates the information.

2. **Invalid Information**
   1. User enters invalid characters or an incorrectly formatted value.
   2. User submits the form.
   3. System identifies the invalid field.
   4. System displays an error message: "Invalid field [X]."
   5. System does not proceed to the next page.
   6. User corrects the information.
   7. System revalidates the information.

3. **Invalid Routing Number**
   1. User enters a routing number that does not meet the required format.
   2. System displays "Invalid routing number."
   3. System prevents the user from proceeding.
   4. User corrects the routing number.
   5. System validates the corrected value.

4. **Invalid Account Number**
   1. User enters an account number containing invalid characters or an unsupported format.
   2. System displays "Invalid account number."
   3. System prevents the user from proceeding.
   4. User corrects the account number.
   5. System validates the corrected value.

5. **Account Already Connected**
   1. User enters information for a bank account that is already connected to their profile.
   2. System identifies that the account is already connected.
   3. System displays a message informing the user.
   4. System does not create a duplicate connection.
   5. User may continue or return to the bank account page.

6. **User Leaves the Page**
   1. User navigates away from the bank account page before submitting.
   2. System does not save incomplete bank information.
   3. System returns the user to the selected page.
   4. User can restart the bank account connection process later.

### Open Issues

1. Can users connect multiple bank accounts?
2. How will sensitive financial information be encrypted?

---

## Use Case 3 - Create a Trip

| **Use Case Title** | Create a Trip |
|---|---|
| **Actors** | Logged-in Web or App User |
| **Preconditions** | Web user has logged in and is on the dashboard page. |
| **Description** | The user creates a new trip by entering basic trip information such as the destination, travel dates, number of travelers, and trip name. The system validates and saves the information and creates a new trip that the user can continue planning. |

### Basic Flow

1. User selects "Create New Trip."
2. System displays the trip creation form.
3. User enters a trip name.
4. User enters the destination.
5. User enters the departure and return dates.
6. User enters the number of travelers.
7. User selects "Create Trip."
8. System validates the entered information.
9. System saves the new trip to the user's account.
10. System displays the new trip's planning page.

### Alternate Flow

1. **User decides not to create a trip**
   1. User selects "Cancel."
   2. The system returns the user to the dashboard.

2. **Required information is missing or invalid**
   1. The system displays an error message and asks the user to correct the information.
   2. The user corrects the information.
   3. If the return date is before the departure date, the system displays an error message and asks the user to enter valid dates.

### Open Issues

1. Should users be able to add multiple destinations to one trip? What would it look like if the user did have multiple trips being planned at once?
2. Should the system automatically calculate the number of days in the trip?
3. Should users be able to invite other users to collaborate on a trip?

---

## Use Case 4 - Create Trip Budget

| **Use Case Title** | Create Trip Budget |
|---|---|
| **Actors** | Logged-in Web or App User |
| **Preconditions** | The user needs to be logged in and has already created a trip. |
| **Description** | The user creates a budget for their trip by entering estimated costs for different travel categories. The system calculates the total trip cost and determines how much the user needs to save. |

### Basic Flow

1. User opens an existing trip.
2. User selects "Create Budget."
3. System displays budget categories for flights, hotel, rental car, food, activities, and shopping.
4. User enters an estimated amount for each category.
5. User enters the amount they have already saved.
6. User enters a savings goal date.
7. User selects "Create Budget."
8. System calculates the total estimated trip cost.
9. System calculates the remaining amount the user needs to save.
10. System calculates a recommended monthly savings amount.
11. System saves and displays the trip budget.

### Alternate Flow

1. **User leaves a budget category blank**
   1. System treats the category as $0 or asks the user to enter an amount.
   2. User can continue creating the budget.

2. **User enters an invalid goal date**
   1. System displays an error message.
   2. User enters a valid future date.
   3. User submits the budget again.

3. **User has already saved enough for the future trip**
   1. System shows that the savings goal has been reached.
   2. No additional monthly savings amount is required.

### Open Issues

1. Should every budget category be required?
2. Should the system suggest estimated amounts for each category?
3. Should users be able to create their own custom budget categories?

---

## Use Case 5 - AI Create & Compare Trip Options

| **Use Case Title** | AI Create & Compare Trip Options |
|---|---|
| **Actors** | Logged-in Web or App User |
| **Preconditions** | User is logged in and has entered basic trip information. |
| **Description** | The user provides their travel preferences and budget. The AI generates trip options based on the user's priorities and allows the user to compare and choose between the generated options. |

### Basic Flow

1. User selects "Plan With AI."
2. System displays a travel preference questionnaire.
3. User enters their preferences, such as budget, luxury, ratings, food, and activities.
4. User submits their preferences.
5. AI generates multiple trip options.
6. System displays the generated options to the user.
7. User compares the trip options.
8. User selects their preferred option.
9. User reviews the AI-generated itinerary.
10. User accepts or edits parts of the itinerary.
11. System saves the selected itinerary to the user's trip.

### Alternate Flow

1. **User does not complete all required preferences**
   1. System displays an error message.
   2. User completes the missing information.
   3. User submits again.

2. **User does not like any AI-generated options**
   1. User selects "Generate New Options."
   2. User changes their preferences if desired.
   3. AI generates new trip options.

3. **User wants to change an AI recommendation**
   1. User removes or edits the recommended item.
   2. User manually adds a replacement.
   3. System updates the itinerary.

### Open Issues

1. How many trip options should the AI generate?
2. What AI model or service will be used?
3. How will the AI get current information about hotels, restaurants, flights, and activities?
4. How much control should the AI have over the user's budget?

---

## Use Case 6 - Search & Add Trip Items

| **Use Case Title** | Search & Add Trip Items |
|---|---|
| **Actors** | Logged-in Web or App User |
| **Preconditions** | User is logged in and has created a trip. |
| **Description** | The user manually searches for flights, hotels, restaurants, or activities and adds selected items to their trip itinerary. |

### Basic Flow

1. User opens an existing trip.
2. User selects a category to search.
3. User chooses flights, hotels, restaurants, or activities.
4. User enters search criteria.
5. System displays matching results.
6. User views the available options.
7. User selects an item.
8. System displays the item's details and estimated cost.
9. User selects "Add to Trip."
10. System adds the item to the itinerary.
11. System updates the trip budget if necessary.

### Alternate Flow

1. **No results are found**
   1. System informs the user that no results were found.
   2. User changes their search criteria.
   3. System performs the search again.

2. **User decides not to add the item**
   1. User returns to the search results.
   2. No changes are made to the trip.

### Open Issues

1. What services or APIs will be used to search for travel options?
2. Should users be able to sort by price, rating, or distance?
3. Will prices update automatically if they change?

---

## Use Case 7 - Track Savings Goal

| **Use Case Title** | Track Savings Goal |
|---|---|
| **Actors** | Normal user |
| **Preconditions** | 1. User must be successfully logged in.<br>2. User must have completed initial account setup.<br>3. User must have created a trip budget.<br>4. User must be at the starting dashboard page. |
| **Description** | The user can view their progress toward their trip savings goal from the dashboard. They can also view more detailed information about their savings, including individual contributions and transactions, and use filters to view their savings by things like date or bank account. |

### Basic Flow

1. User navigates to the dashboard.
2. System displays the user's savings goal progress, including:
   - Visual representation of progress (progress bar, chart, etc.)
   - Amount contributed
   - Total savings goal
   - Remaining amount
   - Trip/time information
   - Estimated goal completion date or savings projection
3. User selects "See More."
4. System displays detailed savings information, including individual contributions/transactions.
5. User selects a filter, such as date range or bank account.
6. System updates the displayed savings information based on the selected filter.
7. User navigates away from the detailed savings page.
8. System removes the selected filters and navigates the user to their chosen page.

### Alternate Flow

1. **No savings contributions match the selected filter**
   1. System displays a message indicating that no savings activity was found.
   2. User may select a different filter.

2. **System is unable to retrieve the required bank information**
   1. System displays an error message.
   2. System does not update the savings information.
   3. User may retry or select a different option.

3. **User's contributions have reached or exceeded the savings goal**
   1. System updates the progress display to reflect the completed goal.
   2. System displays the appropriate remaining amount ($0 if the goal was exactly reached).

4. **User leaves without applying a filter**
   1. System returns the user to their selected destination.

### Open Issues

1. How frequently will bank information be updated?
2. What filters will be available?
3. What exact charts/visualizations will be used?

---

## Use Case 8 - Update Savings Plan

| **Use Case Title** | Update Savings Plan |
|---|---|
| **Actors** | Logged-in Web or App User |
| **Preconditions** | User is logged in and has an existing trip and savings plan. |
| **Description** | The user updates their savings plan by changing their savings goal, target amount, target date, or contribution amount. The system then recalculates the user's savings progress and displays the updated plan. |

### Basic Flow

1. User opens a saved trip.
2. User selects "Savings Plan" for the trip.
3. The system displays the user's current savings plan.
4. User selects "Edit Savings Plan."
5. User updates the desired information, such as target amount, target date, or contribution amount.
6. The user submits the updated information.
7. The system validates the information.
8. System recalculates the savings plan based on the updated information.
9. System displays the updated savings goal and progress.
10. System saves the updated savings plan to the user's account.

### Alternate Flow

1. **Invalid Information**
   1. User enters missing or invalid information.
   2. System detects the invalid information.
   3. System displays an error message.
   4. User corrects the information.
   5. User resubmits the savings plan.
   6. System revalidates the information.

2. **Savings Goal Cannot Be Reached by Target Date**
   1. System determines that the current contribution amount may not meet the savings goal by the target date.
   2. System displays a message informing the user.
   3. User adjusts the contribution amount or target date.
   4. System recalculates the savings plan.

3. **User Cancels the Update**
   1. User selects "Cancel."
   2. System discards the changes.
   3. System keeps the existing savings plan.
   4. System returns the user to the savings plan page.

### Open Issues

1. Should the system automatically calculate how much the user needs to save per week/month?
2. Should the system recommend a new target date if the user cannot reach the savings goal?
3. Should savings progress be based on manually entered savings or connected financial-account data?

---

## Use Case 9 - Invite/Manage Trip Members

| **Use Case Title** | Invite/Manage Trip Members |
|---|---|
| **Actors** | Normal user |
| **Preconditions** | 1. User must be successfully logged in.<br>2. User must have completed initial account setup.<br>3. User must have created a trip plan. |
| **Description** | The user can invite people to join their trip and manage the members of the trip. The trip admin can view member information, add or remove members, and adjust their permissions. |

### Basic Flow

1. User selects the "Invite" button that populates in the same window as the "Start a trip" window.
2. User enters either an email address or phone number of the person they wish to invite.
3. System validates the provided email address or phone number.
4. System sends the invitation to the provided contact information.
5. Recipient receives the invitation and link on their device.
6. Recipient selects the invitation link.
7. System directs the recipient to the sign-up or login page if they are not already logged in.
8. Login page displays "You're invited to join X's trip: [NAME OF TRIP]."
9. Recipient logs in or signs up and joins the trip.
10. Trip owner selects "Manage Trip Members" from the trip page.
11. System displays the current trip members and their information, including when they joined, contact information, and current permissions.
12. Trip owner can add or remove members and change member permissions between owner, edit, and view.
13. System updates the member's access and permission label accordingly.

### Alternate Flow

1. **Invalid email address or phone number**
   1. System displays an error message identifying the invalid field(s).
   2. System highlights the invalid field(s) in red.
   3. User corrects the information and resubmits the invitation.

2. **User attempts to resend an invitation before the cooldown period has ended**
   1. System prevents another invitation from being sent.
   2. System displays a message indicating when the user may resend the invitation.
   3. User may resend the invitation after the cooldown period.

3. **Recipient is already a member of the trip**
   1. System recognizes that the recipient is already a member.
   2. System directs the recipient to their existing trip instead of the sign-up or login page.

4. **Recipient opens an invitation link after it has expired**
   1. System displays a message indicating that the invitation has expired.
   2. System instructs the recipient to request a new invitation from a trip owner.

5. **Trip owner removes a member from the trip**
   1. System revokes the member's access to the shared trip and its details.

6. **Trip owner changes a member's permission**
   1. System updates the member's permissions accordingly.
   2. System updates the permission label displayed next to the member's name.

7. **Member chooses to leave the trip**
   1. System removes the member from the trip.
   2. System revokes the member's access to the shared trip and its details.

8. **Recipient selects an invitation link for a trip that has been deleted**
   1. System determines that the trip no longer exists.
   2. System directs the recipient to a page displaying "This trip no longer exists. Contact the trip owner."

### Open Issues

1. What happens to a member's contributions if they leave the trip?
2. Should there be a limit on the number of members allowed on a trip?
3. How should multiple trip owners be identified and displayed?

---

## Use Case 10 - View/Edit Trip

| **Use Case Title** | View/Edit Trip |
|---|---|
| **Actors** | Normal User |
| **Preconditions** | 1. User must be successfully logged in.<br>2. User must have created a trip or be a member of a trip. |
| **Description** | The user views the details of an existing trip. If the user has permission to edit the trip, they can update trip information such as the trip name, destination, travel dates, and number of travelers. |

### Basic Flow

1. User navigates to the dashboard.
2. System displays the user's existing trips.
3. User selects a trip.
4. System displays the trip details.
5. User selects "Edit Trip."
6. System displays the editable trip information.
7. User changes the desired information.
8. User selects "Save Changes."
9. System validates the updated information.
10. System saves the changes.
11. System displays the updated trip information.

### Alternate Flow

1. **User decides not to save changes**
   1. User selects "Cancel."
   2. System does not save the changes.
   3. System returns the user to the trip page.

2. **User enters invalid information**
   1. System identifies the invalid or missing information.
   2. System displays an error message.
   3. User corrects the information and submits again.

3. **User does not have edit permission**
   1. System allows the user to view the trip.
   2. System prevents the user from editing the trip information.

### Open Issues

1. Which trip information should users be allowed to edit?
2. Should all trip members be notified when trip information changes?
3. Should the system keep a history of changes made to the trip?

---

## Use Case 11 - Track Actual Trip Expenses

| **Use Case Title** | Track Actual Trip Expenses |
|---|---|
| **Actors** | Normal users |
| **Preconditions** | 1. User must be successfully logged in.<br>2. User must have created a trip.<br>3. User must have created a trip budget. |
| **Description** | The user tracks the actual expenses for their trips by entering purchase and other travel costs. The system records the expenses, organizes them by category, and compares the actual spending to the planned trip budget. |

### Basic Flow

1. User opens an existing trip.
2. User selects "Expenses."
3. System displays the trip's current expenses and budget.
4. User selects "Add Expense."
5. User enters the expense amount.
6. User selects a category, such as flight, hotel, food, transportation, activities, or shopping.
7. User enters the date and an optional description.
8. User selects "Save Expense."
9. System validates the entered information.
10. System saves the expense to the trip.
11. System updates the total amount spent.
12. System compares the actual spending to the planned trip budget.

### Alternate Flow

1. **Missing or invalid information**
   1. User leaves a required field blank or enters an invalid amount.
   2. System displays an error message.
   3. User corrects the information and submits again.

2. **User exceeds the trip budget**
   1. System determines that the actual spending exceeds the planned budget.
   2. System displays a message informing the user that they are over budget.

3. **User decides not to add the expense**
   1. User selects "Cancel."
   2. System does not save the expense.
   3. System returns the user to the expenses page.

### Open Issues

1. Should expenses be automatically imported from a connected bank account?
2. Should users be able to upload receipts?
3. Should users be able to edit or delete previously entered expenses?
4. How should shared expenses between trip members be handled?

---

## Use Case 12 - Delete/Cancel Trip

| **Use Case Title** | Delete/Cancel Trip |
|---|---|
| **Actors** | Normal Users |
| **Preconditions** | 1. User must be successfully logged in.<br>2. User must have created a trip.<br>3. User must have permission to delete or cancel the trip. |
| **Description** | The user deletes or cancels an existing trip that they no longer want to plan. The system asks the user to confirm the action before removing the trip. |

### Basic Flow

1. User opens an existing trip.
2. User selects "Delete/Cancel Trip."
3. System displays a confirmation message.
4. User confirms that they want to delete/cancel the trip.
5. System deletes/cancels the trip.
6. System displays a confirmation message.
7. System returns the user to the dashboard.

### Alternate Flow

1. **User decides not to delete/cancel the trip**
   1. User selects "Cancel" on the confirmation message.
   2. System does not delete the trip.
   3. System returns the user to the trip page.

2. **System is unable to delete/cancel the trip**
   1. System displays an error message.
   2. Trip remains unchanged.
   3. User may try again later.

3. **User does not have permission**
   1. System prevents the user from deleting/canceling the trip.
   2. System informs the user that they do not have permission.

### Open Issues

1. Should a deleted trip be permanently deleted or recoverable?
2. Should all trip members be notified when a trip is deleted/canceled?
3. Should only the trip owner be allowed to delete/cancel a trip?
4. What happens to saved expenses, budgets, and itineraries after a trip is deleted?
