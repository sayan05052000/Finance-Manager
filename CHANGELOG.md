# Changelog

All notable changes to the **Finance Manager (FinMngr)** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased] - 2026-11-08

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
