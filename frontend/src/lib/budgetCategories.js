// src/lib/budgetCategories.js
// Icons and descriptions for budget categories, shared by the wizard and the budget page.

export const CATEGORY_META = {
  Flights: { icon: "✈", description: "Round-trip tickets for all travelers" },
  Accommodation: { icon: "⌂", description: "Hotels, rentals and other stays" },
  Food: { icon: "◐", description: "Meals, drinks and snacks" },
  "Local transport": { icon: "⇄", description: "Transfers, transit and car rentals" },
  Activities: { icon: "◇", description: "Tours, attractions and experiences" },
};

export function categoryMeta(name) {
  return CATEGORY_META[name] ?? { icon: "•", description: "Custom category" };
}
