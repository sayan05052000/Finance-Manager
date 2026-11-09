# Changelog

All notable changes to the **Finance Manager (FinMngr)** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v2.1] - 2026-11-09

### Added
- **Holdings Card Direct Add MF Transaction & Locked Auto-Selection**:
  - Added dedicated `[＋ Add MF Tx]` action button and `[📜 Transactions]` history button directly on every Mutual Fund holding card in the **Holdings** screen ([`item_holding.xml`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/res/layout/item_holding.xml) and [`HoldingsAdapter.java`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/ui/adapter/HoldingsAdapter.java)).
  - Launches [`AddMutualFundTransactionBottomSheet`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/ui/dialog/AddMutualFundTransactionBottomSheet.java) via `newInstanceForHolding(Holding)` with the fund scheme name and sub-category strictly auto-selected.
  - Both Fund Scheme and Sub-Category (Savings Type & Fund Sub-Category) are completely locked: disabled with touch rejection (`setOnTouchListener((v, e) -> true)`), preventing any manual changes.
  - Displays a high-contrast amber lock banner at the top of the modal: `🔒 Auto-selected Fund: [SchemeName] • Sub-Category: [SubCat] (Cannot be changed)`.
  - Auto-fills the latest holding NAV and dynamically loads existing folios used specifically for that fund.
- **Account & Loan Direct Transaction Ledger & Management (`AccountTransactionsBottomSheet`)**:
  - Added dedicated interactive bottom sheet modal accessible directly from any Bank Account, Loan Account, or Credit Card item in the **Accounts** screen.
  - Displays filtered transaction history, entry count, total inflows (`+₹...`), total outflows (`-₹...`), and quick shortcuts.
  - Supports all ledger capabilities: tap or long-press to view full transaction details (`TransactionDetailsBottomSheet`), delete transaction with permanent cloud removal, spend reversal confirmation, and direct "Full Ledger ↗" filter redirection.
  - Added `[📜 Transactions]` action pill and real-time transaction count badge directly on each Bank Account and Loan item.
- **Strictly Locked Auto-Selection in Transaction Addition**:
  - When opening Add Transaction from a Bank Account: Loan/Account radio is pre-selected and locked to **Bank Account**, spinner is auto-selected to that specific account, disabled with touch rejection (`setOnTouchListener((v, e) -> true)`), and displays a prominent visual lock banner (`🔒 Auto-selected Account: [Name] (Cannot be changed)`).
  - When opening Add Transaction from a Loan item: Loan/Account radio is pre-selected and locked to **Loan / Card**, spinner is auto-selected to that loan and touch-disabled, and displays `🔒 Auto-selected Loan: [Name] (Cannot be changed)`.
  - When opening Add Transaction from a Credit Card: Loan/Account radio is locked to **Loan / Card**, spinner is locked to `CREDIT CARD`, Credit Card tag section is displayed and locked to that specific credit card with touch rejection, inline card creation is hidden, and displays `🔒 Auto-selected Loan: CREDIT CARD • Card: [CardName] (Cannot be changed)`.
- **Tagged Credit Card Transactions & Lifecycle Restructuring**:
  - Added `[📜 Tagged Transactions (N)]` button to every Credit Card card on the Credit Cards section, allowing immediate review of all expenses tagged to that specific card.
  - Added direct `[＋ Add Tx]` action button on each credit card.
  - Structured card actions into two distinct, balanced rows (Row 1: Transactions & Add Tx; Row 2: Bill Generated, Mark as Paid, Update Bill) completely eliminating horizontal overflow.

### Fixed
- **Cycle Screen Text Invisibility in Light Mode**:
  - Resolved invisible category title text on Cycle Tracker comparison cards (`media_1794194739050_6cb7688d.jpg`), where category names (e.g. *General Household Debit Card Monthly*, *Food*, *Transit*) were hardcoded to `#FFFFFFFF`, making them invisible against light card backgrounds.
  - Replaced hardcoded color with theme-aware `ContextCompat.getColor(requireContext(), R.color.text_primary)` (`#0F172A` in light mode, `#F1F5F9` in dark mode).
  - Unified card styling in [`CycleFragment.java`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/ui/fragment/CycleFragment.java) using `@drawable/bg_card` for consistent 14dp corner radiuses and subtle borders across all screens.
- **10-Minute Periodic Foreground & Standing Background Sync Engine**:
  - Reconfigured auto-sync cadence to exactly 10 minutes (`AUTO_SYNC_INTERVAL_MS = 600,000 ms`).
  - Added standing background synchronization capability using [`BackgroundSyncManager`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/util/BackgroundSyncManager.java) and [`AutoSyncReceiver`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/receiver/AutoSyncReceiver.java).
  - Utilizes `AlarmManager.setExactAndAllowWhileIdle` (RTC_WAKEUP) to reliably sync data with Google Sheets in the background even during Doze mode and when the application is closed or in the background.
  - Automatically reschedules periodic background sync upon device reboot via `RECEIVE_BOOT_COMPLETED`.
  - Automatically evaluates and posts upcoming due date notifications upon background data updates.
- **Bottom Sheet Modal Corner Artifacts Elimination (`BaseBottomSheetDialogFragment`)**:
  - Resolved sharp square black/dark container corners protruding behind curved bottom sheets (`media_1794166006615_ffcbe952.png`).
  - Created [`BaseBottomSheetDialogFragment`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/ui/dialog/BaseBottomSheetDialogFragment.java) enforcing `android.R.color.transparent` on both the window and the internal `design_bottom_sheet` container across all 11 modal subclasses (`AccountTransactionsBottomSheet`, `AddTransactionBottomSheet`, `TransactionDetailsBottomSheet`, `AddCreditCardBottomSheet`, `EditCreditCardBottomSheet`, `AddPolicyBottomSheet`, `EditPolicyBottomSheet`, `EditSipBottomSheet`, `AddMutualFundTransactionBottomSheet`, `SipTracerBottomSheet`, `ViewMfTransactionsBottomSheet`).
  - Updated `FinMngrBottomSheetDialogTheme` in both `values/styles.xml` and `values-night/styles.xml` with transparent backgrounds and `backgroundTint="@android:color/transparent"`.
- **Single-Row Edge-to-Edge Segmented Section Filter Bar**:
  - Completely eliminated excessive empty white/dark space and awkward multi-row wrapping (`media_1794167232755_70c0037f.png`).
  - Engineered a sleek, modern single-line segmented control spanning 100% of the screen width (`match_parent`, 38dp height) with 5 concise, weighted tabs: `All` | `Banks` | `Cards` | `Insurance` | `Goals`.
  - Housed inside a unified rounded track container (`bg_pill_dark`), active filters smoothly transition into an emerald pill (`bg_pill_emerald`) with high-contrast text (`text_on_primary`), saving over 60dp of vertical space for account balances while completely eliminating horizontal scrolling.
- **Stopped & Fully Paid Policy Due Date Status**:
  - Fixed design inconsistency where stopped/matured policies displayed active amber due date badges (`media_1794166074155_f3e8b7b8.jpg`).
  - Added `tvPolicyDueDateLabel` to [`item_policy.xml`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/res/layout/item_policy.xml); when `isPolicyFullyPaidOrMatured(p)` is true, the label automatically displays "Policy Status" with a soft sage green badge (`bg_badge_good`) reading "Stopped / Fully Paid" instead of an amber future payment due prompt.
- **Ledger Filter Spinners Alignment & Touch Target**:
  - Updated [`item_spinner_selected.xml`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/res/layout/item_spinner_selected.xml) to center text vertically with `layout_height="match_parent"` and balanced internal padding, preventing text cropping.
  - Increased Ledger filter spinner heights to 42dp in [`fragment_ledger.xml`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/res/layout/fragment_ledger.xml) for optimal touch targets and spacing.
- **Complete Elimination of Horizontal Scrolling Across the Entire Application**:
  - Replaced all 5 legacy `HorizontalScrollView` widgets with responsive, screen-bounded multi-row grid layouts:
    1. `fragment_accounts.xml`: Converted top section filter pills into a sleek single-line segmented control with zero horizontal scroll.
    2. `dialog_select_month_year.xml`: Converted month selector and year selector from horizontal carousels into structured 4-column responsive grids.
    3. `dialog_sip_tracer.xml`: Converted month buttons into a 4-column calendar grid.
    4. `item_credit_card.xml`: Restructured actions into two vertical rows of action chips with zero horizontal scrolling.
- **Bottom Navigation Bar Number Overflow**:
  - Completely eliminated overflowing numerical badge indicators (`5`, `13`) over bottom navigation icon labels (`media_1794164409861_76e5a574.jpg`), establishing a sleek, un-cluttered bottom navigation bar.
