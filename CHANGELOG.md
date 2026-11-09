# Changelog

All notable changes to the **Finance Manager (FinMngr)** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v2.2] - 2026-11-09

### Added
- **Financial Goals Dynamic Schema & Contribution JSON Integration**:
  - Adapted to the user's updated Google Sheet `Goals` schema: `[Goals Name, Goal Amount, Occured Till Date, Achived Till Date, Contribution JSON]`.
  - Added JSON contribution mapping in [`GoalItem.java`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/data/model/GoalItem.java) linking goals to specific Bank Accounts (`HDFC SAVINGS`, `PNB`, `SBI`, etc.) and Mutual Fund categories (`Mutual Fund`, `Family Savings`, etc.) with individual allocation weights.
  - Engineered local spreadsheet-parity formula evaluation in [`CalculationEngine.java`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/data/CalculationEngine.java), dynamically aggregating real-time account balances and mutual fund portfolio valuations.
  - Added human-readable contribution summaries to goal cards via `getContributionSummary()` (e.g., `Linked: HDFC SAVINGS, PNB • MF (30%)`).
- **Interactive Add Goal Workflow (`AddGoalBottomSheet`)**:
  - Added `[＋ Add Goal]` action pill in `fragment_accounts.xml` launching [`AddGoalBottomSheet`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/ui/dialog/AddGoalBottomSheet.java).
  - Supports Goal Name, Target Amount, Initial Amount, and Contribution Asset Linker (Manual Allocation, Bank Account Link, or Mutual Fund Category Link with percentage).
  - Appends new rows to Google Sheets via `action === 'addGoal'` in `androidscript.gs` with instant optimistic UI update.
- **Goal Removal Workflow (`[🗑️ Remove]`)**:
  - Replaced legacy view-only labels with interactive `[🗑️ Remove]` action pill on each goal card in [`item_goal.xml`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/res/layout/item_goal.xml).
  - Prompts with safety confirmation dialog before permanently removing the goal from Google Sheets (`action === 'deleteGoal'`) and local cache.
- **Google Apps Script Goals Sync Endpoints (`androidscript.gs`)**:
  - Added `addGoal` POST action appending `[name, targetAmount, occuredTillDate, "=MAP(...)", contributionJson]`.
  - Added `deleteGoal` POST action searching by Goal Name and deleting the corresponding row.
  - Updated `getGoals(ss)` to read `Achived Till Date` and `Contribution JSON`.

### Fixed
- **Bottom Sheet Modal Corner Artifacts Elimination (Hardening)**:
  - Enforced `onStart()` background stripping on both `design_bottom_sheet` and view parent in [`BaseBottomSheetDialogFragment.java`](file:///c:/Users/samiy/OneDrive/Documents/projects/android/Finance-Manager/finance-android/app/src/main/java/com/samiy/financemanager/ui/dialog/BaseBottomSheetDialogFragment.java).
  - Set `shapeAppearance="@null"`, `shapeAppearanceOverlay="@null"`, and `elevation="0dp"` in `FinMngrBottomSheetModal` style, completely eliminating grey rectangular corner artifacts behind curved modals.

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

---

## [v2.0] - 2026-11-08

### Fixed
- **Instant Theme Switching Reflection on Current Screen**:
  - Removed `uiMode` from `android:configChanges` in `AndroidManifest.xml`, which was previously preventing Android from recreating `MainActivity` and forcing the user to switch screens to view theme changes.
  - Implemented synchronous preference commitment (`.commit()`) in `ThemeManager.java` and explicit `recreate()` dispatch upon theme toggle.
  - Added active tab state preservation in `MainActivity` (`saved_tab_id` bundle persistence + `selectTabById()`), ensuring that the application reopens seamlessly on the exact screen/fragment the user was viewing, fully re-rendered with the new theme colors.
  - Added queued floating toast notification (`hasPendingThemeToast()` / `consumePendingThemeToast()`) displayed cleanly on resume of the newly themed activity.
- **Pastel Light Theme Overhaul & Contrast Restoration**:
  - Replaced flat `#F8FAFC` background with soothing porcelain cool slate (`#F1F5F9`), providing crisp visual definition and tactile card separation for pure white cards (`#FFFFFF`) with `@color/border_dark` (`#E2E8F0`).
  - Fixed washed-out text and numbers by upgrading `green_mint` from low-contrast `#10B981` (2.04:1 ratio) to deep forest sage `#047857` (5.5:1 WCAG AAA compliance), ensuring that Net Worth, active status chips, credits, and balance indicators are crisp and legible.
  - Added semantic `text_on_primary` attribute (`#FFFFFF` in light mode, `#0B0F19` in dark mode) ensuring optimal contrast across all primary pill buttons and active filter pills.
  - Harmonized pastel accent washes: `green_bg` (`#ECFDF5`), `red_bg` (`#FFF1F2`), `amber_bg` (`#FEF3C7`), `blue_bg` (`#F0F9FF`), and `red_rose` (`#E05263`).
  - Fixed wordmark letter "F" in "FinMngr" by migrating from hardcoded `#FFFFFF` to dynamic `@color/text_primary`, making it visible in both themes.

### Added
- **Design Language System Harmonization & Unification**:
  - Eliminated legacy unstyled flat `<Button>` tags across all modals (`dialog_edit_credit_card`, `dialog_add_credit_card`, `dialog_add_transaction`, `dialog_add_mf_transaction`, `dialog_edit_sip`, `dialog_select_folio`, `dialog_select_subcategory`, `fragment_holdings`), replacing them with cohesive `com.google.android.material.button.MaterialButton` pill elements with standard 21-25dp radii and `textAllCaps="false"`.
  - Added dedicated `@drawable/bg_bottom_sheet` (24dp top corner radius with clean subtle borders) paired with `FinMngrBottomSheetDialogTheme` in `styles.xml` to eliminate harsh square container edges and background clipping.
  - Standardized all modal date inputs with calendar pickers (`@drawable/ic_calendar` tinted with `@color/colorPrimary`).
  - Removed inconsistent emoji headers ("✏️ Update Credit Card", "💳 Add Credit Card", "💳 Credit Cards") in favor of clean typography and unified subtitle hierarchy across the entire app.
- **Instant Optimistic UI Updates & Global Sync Feedback Engine**:
  - Local state in `FinanceRepository` (such as Credit Card bill generation, payment recording, and transaction mutations) updates in-memory immediately with zero wait time ($0\text{ ms}$ UI latency) and invokes `notifySyncListeners()`. The UI recalculates balances and renders updated cards instantly without waiting for Google Apps Script roundtrips (which take 8–15 seconds).
  - Added global visual syncing indicators to keep user informed of ongoing cloud background sync:
    - A slim indeterminate progress bar (`progressBarGlobalSync`) docked at the top of the screen in `activity_main.xml`.
    - Continuous smooth 360-degree rotational animation on the header refresh button (`btnRefresh`) while any background sync or mutation is active.
  - Added `SyncStatusListener` in `FinanceRepository` so any network operation triggers global syncing indicators and reports completion or errors reliably.
- **Universal In-App Floating Toaster HUD (`FinMngrToast`)**:
  - Replaced native unstyled Android OS toasts across all fragments and dialogs with `FinMngrToast`.
  - Floating pill HUD (`layoutInAppToast`) anchored gracefully above the bottom navigation bar with pastel styling (Sage Mint for success, Honey Amber for warnings, Rose Coral for errors/destructive actions).
  - Smooth slide-up fade animations with auto-dismissal.
- **Universal Modal & Confirmation Dialog Engine (`FinMngrDialog`)**:
  - Replaced unstyled system `AlertDialog` dialogs (Bill Generated, Mark Paid, Delete Transaction, Google Account switcher) with unified `FinMngrDialog` modal engine (`dialog_finmngr_modal.xml`).
  - Features 24dp rounded corners, app branding icon, distinct titles and descriptions, and matching `MaterialButton` action pills (Cancel and Pastel Accent Confirm/Delete).
- **Bug Fix in Bill Generated Amount**:
  - Fixed credit card bill generation toast display bug: previously evaluated post-mutation `card.unbilledAmount` showing "₹0 billed"; now accurately caches `amountToBill` before mutation and reports the exact billed amount (e.g., "₹6,102.33 billed").
- **Pastel Color Design System & Dynamic Theme Switcher**:
  - Implemented cohesive pastel visual design with dual palettes:
    - **Pastel Dark (Obsidian / Sage)**: Soothing desaturated pastel tones engineered for low light and OLED displays.
      - Background: Obsidian Slate (`#0B0F19`)
      - Surface / Cards: Dark Navy Slate (`#131B26`)
      - Primary / Sage Mint: (`#6EE7B7`)
      - Secondary / Rose / Expense: (`#FDA4AF`)
      - Warning / Honey Amber: (`#FDE68A`)
      - Info / Powder Sky: (`#BAE6FD`)
      - Card Strokes & Dividers: Muted Charcoal (`#1E293B`)
    - **Pastel Light (Porcelain / Mint)**: Crisp, high-clarity daylight theme with gentle pastel accents.
      - Background: Soft Porcelain (`#F8FAFC`)
      - Surface / Cards: Pure White (`#FFFFFF`)
      - Primary / Forest Mint: (`#059669`)
      - Secondary / Rose Coral: (`#E11D48`)
      - Warning / Amber: (`#D97706`)
      - Info / Sky Blue: (`#0284C7`)
      - Card Strokes & Dividers: Clean Slate (`#E2E8F0`)
  - **1-Tap Dynamic Theme Toggle (`btnThemeToggle`)**:
    - Added theme switcher action icon with vector glyph (`ic_theme_toggle.xml`) directly to the top app header in `activity_main.xml`.
    - Features smooth 360-degree rotation animation upon toggling.
    - Displays instant user feedback toast confirming active theme ("Switched to Pastel Light" / "Switched to Pastel Dark").
  - **`ThemeManager` Persistent State Engine**:
    - New `ThemeManager.java` utility persisting user theme selection in `SharedPreferences` (`finmngr_theme_prefs`).
    - Applies user preference synchronously at startup via `AppCompatDelegate.setDefaultNightMode()`.
    - Supports `toggleTheme()`, `applyTheme()`, `isLightPastel()`, and `getSelectedTheme()`.
  - **Dynamic Theme-Aware Custom Views & Drawables**:
    - `CycleChartView`: All canvas paint colors (budget lines, expense bars, investment fills, axis grid, selection cursor, highlight badges) now resolve dynamically at runtime via `ContextCompat.getColor()` responding immediately to theme changes.
    - Bottom Navigation: Added state-aware `nav_item_tint.xml` selector making the active tab glow with the pastel primary color while inactive tabs stay muted.
    - Badge Drawables: Converted all badge backgrounds (`bg_badge_amber.xml`, `bg_badge_good.xml`, `bg_badge_red.xml`, `bg_badge_warning.xml`, `bg_badge_loan.xml`) to use semantic color attributes.
    - Vector Icons: Updated `ic_calendar.xml`, `ic_refresh.xml`, and dialog dividers to adapt dynamically.
    - Status Bar Light Mode: Automatically manages `windowLightStatusBar` in `values/styles.xml` (true for light theme, false for dark theme).

### Changed
- Bumped application version to **v2.0** (`versionCode 11`, `versionName "2.0"`).
- Updated `README.md` with complete documentation of the Pastel Design System and dynamic theme switcher.

---

## [v1.9] - 2026-11-08

### Added
- **100% Dynamic Column Order Resilience in Google Apps Script (`androidscript.gs`)**:
  - Implemented `findTransactionsColumnIndices(headers)` to dynamically scan row 1 headers at runtime and resolve column indices for all 15 transaction attributes: `Date`, `Credit`, `Debit`, `Net`, `Remainder`, `Sub Category`, `Type`, `Loans`, `Account`, `Category`, `Year`, `Month`, `Debit Without Sign`, `Tagged Credit Card`, and `Scanned / Processed Flag`.
  - Reordered columns in the Google Sheet (e.g. moving `Tagged Credit Card` to Column 14 and `Status / Scanned` to Column 15) no longer break read (`getTransactions`), create (`addTransaction`), edit (`updateTransaction`), delete (`deleteTransaction`), or remainder calculations (`calculateRemainders`).
  - `calculateRemainders()` now dynamically detects the target `Remainder` column and batch-writes balances directly to that column instead of assuming hardcoded Column E.
- **Credit Cards Management Engine (`AccountsFragment` & `Credit Cards` Sheet)**:
  - Added dedicated `Credit Cards` sheet integration with 6 core columns: `Card Name`, `Billed Amount`, `Unbilled Amount`, `Due Date`, `Last Paid`, `Status`.
  - Displayed credit card cards in `AccountsFragment` under the Policies section alongside insurance policies.
  - Added **`+ Add Card`** modal (`AddCreditCardBottomSheet`) with mandatory unique card name verification, optional due date picker, optional billed amount, and initialized zero unbilled amount.
  - Added **`✓ Mark as Paid`** 1-tap action that automatically advances the due date by exactly 1 calendar month ($m \rightarrow m + 1$), resets billed amount to ₹0.00, sets status to `Paid`, and sets last paid date to today without requiring manual calendar input.
  - Added **`⚡ Bill Generated`** 1-tap action that atomically converts `Unbilled Amount` into active `Billed Amount` and resets `Unbilled Amount` to ₹0.00.
  - Added **`✏️ Update Due / Bill`** modal (`EditCreditCardBottomSheet`) strictly isolating metadata edits from the payment workflow to prevent accidental mark-as-paid invocations.
- **Credit Card Transaction Tagging & Spend Reversals**:
  - Added **"Tag to Credit Card"** dropdown in `AddTransactionBottomSheet` when the loan account is selected as `CREDIT CARD`.
  - Allows inline credit card registration directly from the transaction creation flow via `＋ Add Card`.
  - Tagging a transaction to a credit card automatically increments that card's unbilled balance in both local memory and Google Sheets:
    $$\text{Unbilled Amount}_{\text{new}} = \text{Unbilled Amount}_{\text{current}} + \text{Transaction Net}$$
  - Added **Automatic Spend Reversal on Delete**: Deleting a transaction tagged to a credit card immediately decrements its contribution from the card's unbilled balance ($\max(0, \text{Unbilled} - \text{Delta})$).
  - Added single-credit-card fallback so existing transactions created prior to explicit tagging automatically resolve to the user's active credit card.
- **Transaction Details BottomSheet Modal (`TransactionDetailsBottomSheet`)**:
  - Tapping any transaction item in `LedgerFragment` opens a comprehensive inspection modal displaying:
    - Signed hero amount with color coding (emerald for credit, rose for debit).
    - Transaction type badge (`EXPENSE`, `INCOME`, `INVESTMENT`).
    - Balancing out indicator badge (`Balancing Out`).
    - Tagged credit card badge (`[💳 Card Name]`).
    - Date and effective billing cycle badge.
    - Category & Sub-Category breadcrumbs.
    - Source account or loan account.
    - Chronological running balance remainder.
    - Google Sheets row ID reference.
  - **Accidental Deletion Prevention**: Deleting a transaction now requires explicitly tapping the prominent `"🗑️ Delete Transaction"` button within the details modal or confirmation dialog, eliminating accidental swipes or card-tap deletions.
- **Ledger UI Enhancements for Credit Cards**:
  - Added Sky Blue `[💳 Card Name]` chip badge (`tvTxCardBadge`) in Row 1 of transaction cards in `LedgerFragment`.
  - Appended credit card metadata to Row 2 category description (e.g., `Shopping • Personal • 💳 Tata Neu Infinity`).
- **Proactive Upcoming Due Date Reminders & Daily Notifications**:
  - Added Overview screen countdown toasters (`item_due_reminder.xml`) displaying active alerts for:
    - LIC & Tata AIA policies with due dates within 14 days.
    - Credit cards with billed amount > 0 and due dates within 14 days.
  - Displays dynamic countdown pills (`Due in X days`, `Due Today`, `Overdue by X days`) that decrement daily.
  - Scheduled daily system background notification via Android `WorkManager` (`DueReminderWorker`) delivering exactly 1 notification per qualifying entity per day.

### Changed
- Configured default fallback index for `Tagged Credit Card` to Column 14 (Column N) and `Scanned For Budget Analysis / Processed` to Column 15 (Column O) in `androidscript.gs`.
- Updated `README.md` to document dynamic column header order resilience across both `Transactions` and `Credit Cards` sheets.
- Updated `FinanceRepository.java` to support unbilled amount mutations, credit card bill generation, due date rollovers, and transaction deletion reversals.

### Removed
- **Removed Deprecated & Dead Posting Actions (`androidscript.gs`)**:
  - Eliminated `action === 'updateTransaction'` and `action === 'addHolding'` endpoints from the Google Apps Script Web App gateway to strictly align with the add-and-delete-only transaction accounting model and active UI capabilities.
- **Removed Dead Code & Deprecated Components from Android Client**:
  - Removed unused `FinanceRepository.updateTransaction()` method.
  - Deleted orphaned `EditTransactionBottomSheet.java` dialog and `dialog_edit_transaction.xml` layout, fully standardizing transaction inspection on `TransactionDetailsBottomSheet`.

---

## [v1.8] - 2026-11-07

### Added
- **1-Tap 'Mark as Paid' for Insurance Policies (`AccountsFragment`)**:
  - Automatically advances due date by +1 year ($y \rightarrow y + 1$), capped by policy maturity date.
  - Updates last paid date to today, sets status to `Active`, and flushes immediately to Google Sheets.
- **Transaction Add & Delete Only Architecture**:
  - Removed transaction editing to maintain financial audit integrity and prevent historical balance corruption.
- **Universal Custom Calendar Dialog V2 (`CustomCalendarDialog.java`)**:
  - Dark-themed dialog matching FinMngr design system with custom header, day grid, and year pickers.

### Changed
- **Zero-Latency Dynamic Cache-Busting Engine**:
  - Appended high-resolution timestamps and cryptographically unique nonces to all GET requests to bypass ISP and Google Front End (GFE) intermediate caches.
  - Implemented optimistic in-memory transaction preservation (`mergeRecentLocalTransactions`) to eliminate UI flicker while Google Sheets replica propagation occurs.
- **Server-Side Flush Pipeline**:
  - Added `SpreadsheetApp.flush()` before and after running balance calculations in `androidscript.gs` to guarantee immediate persistence.

---

## [v1.0.0] - [v1.7.0] - Baseline Core Architecture

### Added
- **Core Financial Tracking**:
  - Income, Expense, and Investment tracking with 6th-to-5th monthly billing cycles.
  - Chronological running balances per bank account and loan account via `calculateRemainders()`.
  - Mutual fund portfolio tracker with live NAV updates and Buy/Redeem transaction logging.
  - SIP Tracer for monthly outlay tracking and execution verification.
  - Interactive Cycle Analytics charts comparing Budget vs. Actual Spends vs. Investments.
  - Google Account OAuth 2.0 integration with strict access control and email whitelisting.
- **Google Apps Script REST Gateway**:
  - Serverless Google Apps Script Web App providing bidirectional synchronization between Google Sheets and Android client.
- **Custom Build & Packaging System**:
  - Out-of-tree NVMe temporary build directory routing to avoid OneDrive file-locking thrashing.
  - Automatic signed APK mirroring to `FinanceManager.apk` at the repository root.
