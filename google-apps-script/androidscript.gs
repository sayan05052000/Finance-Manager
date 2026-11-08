/**
 * Google Apps Script Web App for Finance Manager (Android & Web)
 * File: androidscript.gs
 *
 * Deploy as Web App:
 * 1. Open Google Sheet > Extensions > Apps Script.
 * 2. Paste this code into androidscript.gs (delete Code.gs if present).
 * 3. Click Deploy > New deployment (or Manage deployments > edit > New version).
 * 4. Execute as: "Me" | Who has access: "Anyone".
 */

function doGet(e) {
  try {
    const action = (e && e.parameter && e.parameter.action) || 'all';
    const ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === 'recalculateRemainders') {
      if (typeof calculateRemainders === 'function') {
        calculateRemainders();
      }
      return jsonResponse({
        status: 'success',
        message: 'Remainders and account/loan balances recalculated successfully'
      });
    }

    if (action === 'all') {
      return jsonResponse({
        status: 'success',
        data: {
          accounts: getAccounts(ss),
          loans: getLoans(ss),
          budget: getBudget(ss),
          goals: getGoals(ss),
          holdings: getHoldings(ss),
          mutualFundTransactions: getMutualFundTransactions(ss),
          insurance: getInsurance(ss),
          sips: getSips(ss),
          categories: getCategories(ss),
          expenseTracker: getExpenseTracker(ss),
          transactions: getTransactions(ss),
          creditCards: getCreditCards(ss)
        }
      });
    }

    if (action === 'transactions') {
      const limit = parseInt(e.parameter.limit || '500', 10);
      const page = parseInt(e.parameter.page || '1', 10);
      return jsonResponse({
        status: 'success',
        data: getTransactions(ss, limit, page)
      });
    }

    if (action === 'summary') {
      return jsonResponse({
        status: 'success',
        data: {
          accounts: getAccounts(ss),
          loans: getLoans(ss),
          budget: getBudget(ss),
          goals: getGoals(ss)
        }
      });
    }

    return jsonResponse({ status: 'error', message: 'Unknown action: ' + action });
  } catch (err) {
    return jsonResponse({ status: 'error', message: err.toString() });
  }
}

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents || '{}');
    const action = data.action || (e.parameter && e.parameter.action);
    const ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === 'addTransaction') {
      const sheet = ss.getSheetByName('Transactions');
      if (!sheet) throw new Error('Transactions sheet not found');

      const tx = data.transaction || {};
      const date = tx.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'dd/MM/yyyy');
      const credit = (tx.credit !== undefined && tx.credit !== null && tx.credit !== '' && Number(tx.credit) > 0) ? Number(tx.credit) : 0;
      let debitWithoutSign = (tx.debitWithoutSign !== undefined && tx.debitWithoutSign !== null && tx.debitWithoutSign !== '' ) ? Number(tx.debitWithoutSign) : 0;
       let debitVal = -1 * debitWithoutSign
      const net = (credit !== '' ? Number(credit) : 0) + (debitVal !== '' ? Number(debitVal) : 0);
      const remainder = '';
      const subCategory = tx.subCategory || '';
      const type = tx.type || 'Expense';
      const loans = tx.loans || '';
      const account = tx.account || '';
      const category = tx.category || '';
      const parts = String(date).split('/');
      const year = tx.year || (parts.length === 3 ? parts[2] : new Date().getFullYear());
      const month = tx.month || (parts.length >= 2 ? parts[1] : (new Date().getMonth() + 1));

      const transHeaders = sheet.getRange(1, 1, 1, Math.max(15, sheet.getLastColumn())).getValues()[0];
      const cols = findTransactionsColumnIndices(transHeaders);

      const taggedCard = tx.taggedCreditCard || tx.creditCard || '';

      if (cols.colTaggedCard > sheet.getLastColumn() || !sheet.getRange(1, cols.colTaggedCard).getValue()) {
        sheet.getRange(1, cols.colTaggedCard).setValue('Tagged Credit Card');
      }

      const maxCol = Math.max(
        sheet.getLastColumn(),
        cols.colDate, cols.colCredit, cols.colDebit, cols.colNet,
        cols.colRemainder, cols.colSubCategory, cols.colType, cols.colLoans,
        cols.colAccount, cols.colCategory, cols.colYear, cols.colMonth,
        cols.colDebitWithoutSign, cols.colProcessed, cols.colTaggedCard
      );
      const newRow = new Array(maxCol).fill('');

      if (cols.colDate > 0) newRow[cols.colDate - 1] = date;
      if (cols.colCredit > 0 && credit > 0) newRow[cols.colCredit - 1] = credit;
      if (cols.colSubCategory > 0) newRow[cols.colSubCategory - 1] = subCategory;
      if (cols.colType > 0) newRow[cols.colType - 1] = type;
      if (cols.colLoans > 0) newRow[cols.colLoans - 1] = loans;
      if (cols.colAccount > 0) newRow[cols.colAccount - 1] = account;
      if (cols.colCategory > 0 && category) newRow[cols.colCategory - 1] = category;
      if (cols.colYear > 0 && year) newRow[cols.colYear - 1] = year;
      if (cols.colMonth > 0 && month) newRow[cols.colMonth - 1] = month;
      if (cols.colDebitWithoutSign > 0) newRow[cols.colDebitWithoutSign - 1] = (debitWithoutSign !== '' ? debitWithoutSign : 0);
      if (cols.colTaggedCard > 0 && taggedCard) newRow[cols.colTaggedCard - 1] = taggedCard;

      sheet.appendRow(newRow);

      // If tagged to a credit card, update its Unbilled Amount in 'Credit Cards' sheet
      if (taggedCard) {
        try {
          const ccSheet = ss.getSheetByName('Credit Cards');
          if (ccSheet && ccSheet.getLastRow() >= 2) {
            const ccHeaders = ccSheet.getRange(1, 1, 1, ccSheet.getLastColumn()).getValues()[0];
            const cols = findCreditCardColumnIndices(ccHeaders);
            const ccValues = ccSheet.getDataRange().getValues();
            const targetName = String(taggedCard).toLowerCase().trim();
            for (let i = 1; i < ccValues.length; i++) {
              const rowCardName = String(ccValues[i][cols.colCardName - 1] || '').toLowerCase().trim();
              if (rowCardName === targetName) {
                const currUnbilled = Number(ccValues[i][cols.colUnbilled - 1]) || 0;
                let delta = 0;
                if (debitWithoutSign > 0) {
                  delta += debitWithoutSign;
                }
                if (credit > 0) {
                  delta -= credit;
                }
                if (delta === 0 && tx.net !== undefined) {
                  delta = Math.abs(Number(tx.net) || 0);
                }
                let newUnbilled = currUnbilled + delta;
                if (currUnbilled >= 0 && newUnbilled < 0) newUnbilled = 0;
                ccSheet.getRange(i + 1, cols.colUnbilled).setValue(Math.round(newUnbilled * 100) / 100);
                break;
              }
            }
          }
        } catch (ccErr) {
          console.warn('Error updating credit card unbilled amount: ' + ccErr);
        }
      }

      if (typeof calculateRemainders === 'function') {
        try {
          calculateRemainders();
          SpreadsheetApp.flush();
        } catch (err) {
          console.warn('calculateRemainders error: ' + err);
        }
      }

      return jsonResponse({ status: 'success', message: 'Transaction added successfully' });
    }

    if (action === 'updateTransaction') {
      const sheet = ss.getSheetByName('Transactions');
      if (!sheet) throw new Error('Transactions sheet not found');

      const tx = data.transaction || {};
      let rowId = Number(tx.id);

      const transHeaders = sheet.getRange(1, 1, 1, Math.max(15, sheet.getLastColumn())).getValues()[0];
      const cols = findTransactionsColumnIndices(transHeaders);

      // Fallback row matching if rowId is invalid or was temporary
      if (!rowId || rowId < 2 || rowId > sheet.getLastRow()) {
        const vals = sheet.getDataRange().getValues();
        for (let i = vals.length - 1; i >= 1; i--) {
          const rDate = cols.colDate > 0 ? (vals[i][cols.colDate - 1] instanceof Date ? Utilities.formatDate(vals[i][cols.colDate - 1], 'Asia/Kolkata', 'dd/MM/yyyy') : String(vals[i][cols.colDate - 1] || '').trim()) : '';
          const rSub = cols.colSubCategory > 0 ? String(vals[i][cols.colSubCategory - 1] || '').trim() : '';
          if (tx.date && rDate === String(tx.date).trim() && (!tx.subCategory || rSub === String(tx.subCategory).trim())) {
            rowId = i + 1;
            break;
          }
        }
      }

      if (!rowId || rowId < 2 || rowId > sheet.getLastRow()) {
        throw new Error('Invalid transaction row ID: ' + tx.id);
      }

      // Compute consistent credit, debit, debitWithoutSign, and net
      const creditVal = (tx.credit !== undefined && tx.credit !== null && tx.credit !== '' && Number(tx.credit) > 0) ? Number(tx.credit) : 0;
      let dwsVal = (tx.debitWithoutSign !== undefined && tx.debitWithoutSign !== null && tx.debitWithoutSign !== '') ? Number(tx.debitWithoutSign) : 0;

      // Dynamic column writes
      if (cols.colDate > 0 && tx.date !== undefined && tx.date !== null && String(tx.date).trim() !== '') {
        const dStr = String(tx.date).trim();
        sheet.getRange(rowId, cols.colDate).setValue(dStr);
        const parts = dStr.split('/');
        if (parts.length === 3) {
          if (cols.colYear > 0) sheet.getRange(rowId, cols.colYear).setValue(parts[2]);
          if (cols.colMonth > 0) sheet.getRange(rowId, cols.colMonth).setValue(parts[1]);
        }
      }

      if (cols.colCredit > 0) {
        sheet.getRange(rowId, cols.colCredit).setValue(creditVal > 0 ? creditVal : '');
      }

      if (cols.colSubCategory > 0 && tx.subCategory !== undefined && tx.subCategory !== null) {
        sheet.getRange(rowId, cols.colSubCategory).setValue(String(tx.subCategory).trim());
      }

      if (cols.colType > 0 && tx.type !== undefined && tx.type !== null) {
        sheet.getRange(rowId, cols.colType).setValue(String(tx.type).trim());
      }

      if (cols.colLoans > 0) {
        sheet.getRange(rowId, cols.colLoans).setValue(tx.loans ? String(tx.loans).trim() : '');
      }

      if (cols.colAccount > 0) {
        sheet.getRange(rowId, cols.colAccount).setValue(tx.account ? String(tx.account).trim() : '');
      }

      if (cols.colCategory > 0 && tx.category !== undefined && tx.category !== null && String(tx.category).trim() !== '') {
        sheet.getRange(rowId, cols.colCategory).setValue(String(tx.category).trim());
      }

      if (cols.colDebitWithoutSign > 0) {
        sheet.getRange(rowId, cols.colDebitWithoutSign).setValue(dwsVal);
      }

      const taggedCard = tx.taggedCreditCard || tx.creditCard;
      if (cols.colTaggedCard > 0 && taggedCard !== undefined) {
        sheet.getRange(rowId, cols.colTaggedCard).setValue(String(taggedCard).trim());
      }

      if (typeof calculateRemainders === 'function') {
        try {
          calculateRemainders();
        } catch (err) {
          console.warn('calculateRemainders error: ' + err);
        }
      }

      return jsonResponse({ status: 'success', message: 'Transaction updated successfully' });
    }

    if (action === 'deleteTransaction') {
      const sheet = ss.getSheetByName('Transactions');
      if (!sheet) throw new Error('Transactions sheet not found');

      const tx = data.transaction || {};
      let rowId = Number(data.id || tx.id);

      const transHeaders = sheet.getRange(1, 1, 1, Math.max(15, sheet.getLastColumn())).getValues()[0];
      const cols = findTransactionsColumnIndices(transHeaders);

      // Fallback row matching if rowId is invalid or was temporary
      if (!rowId || rowId < 2 || rowId > sheet.getLastRow()) {
        const vals = sheet.getDataRange().getValues();
        for (let i = vals.length - 1; i >= 1; i--) {
          const rDate = cols.colDate > 0 ? (vals[i][cols.colDate - 1] instanceof Date ? Utilities.formatDate(vals[i][cols.colDate - 1], 'Asia/Kolkata', 'dd/MM/yyyy') : String(vals[i][cols.colDate - 1] || '').trim()) : '';
          const rSub = cols.colSubCategory > 0 ? String(vals[i][cols.colSubCategory - 1] || '').trim() : '';
          if (tx.date && rDate === String(tx.date).trim() && (!tx.subCategory || rSub === String(tx.subCategory).trim())) {
            rowId = i + 1;
            break;
          }
        }
      }

      if (!rowId || rowId < 2 || rowId > sheet.getLastRow()) {
        throw new Error('Invalid transaction row ID: ' + (data.id || tx.id));
      }

      // Read row values before deleting to reverse credit card effect if tagged
      const readCols = Math.max(sheet.getLastColumn(), cols.colTaggedCard, cols.colDebitWithoutSign, cols.colCredit, cols.colDebit, cols.colLoans);
      const rowValues = sheet.getRange(rowId, 1, 1, readCols).getValues()[0];
      const rowCredit = cols.colCredit > 0 ? (Number(rowValues[cols.colCredit - 1]) || 0) : 0;
      const rowDebitWithoutSign = cols.colDebitWithoutSign > 0 ? (Number(rowValues[cols.colDebitWithoutSign - 1]) || 0) : 0;
      const rowDebit = cols.colDebit > 0 ? (Number(rowValues[cols.colDebit - 1]) || 0) : 0;
      const rowLoan = cols.colLoans > 0 ? String(rowValues[cols.colLoans - 1] || '').trim() : '';
      let rowTaggedCard = (cols.colTaggedCard > 0 && rowValues.length >= cols.colTaggedCard && rowValues[cols.colTaggedCard - 1]) ? String(rowValues[cols.colTaggedCard - 1]).trim() : '';
      if (!rowTaggedCard && tx.taggedCreditCard) {
        rowTaggedCard = String(tx.taggedCreditCard).trim();
      }
      if (!rowTaggedCard && (data.taggedCreditCard || data.creditCard)) {
        rowTaggedCard = String(data.taggedCreditCard || data.creditCard).trim();
      }
      if (!rowTaggedCard && rowLoan.toUpperCase() === 'CREDIT CARD') {
        const ccSheet = ss.getSheetByName('Credit Cards');
        if (ccSheet && ccSheet.getLastRow() >= 2) {
          const ccHeaders = ccSheet.getRange(1, 1, 1, ccSheet.getLastColumn()).getValues()[0];
          const ccCols = findCreditCardColumnIndices(ccHeaders);
          const ccValues = ccSheet.getDataRange().getValues();
          if (ccValues.length === 2) {
            rowTaggedCard = String(ccValues[1][ccCols.colCardName - 1] || '').trim();
          }
        }
      }

      let dws = rowDebitWithoutSign;
      if (dws === 0 && tx.debitWithoutSign) dws = Number(tx.debitWithoutSign) || 0;
      let cr = rowCredit;
      if (cr === 0 && tx.credit) cr = Number(tx.credit) || 0;
      let delta = 0;
      if (dws > 0) delta += dws;
      if (cr > 0) delta -= cr;
      if (delta === 0 && (rowDebit !== 0 || tx.debit || tx.net)) {
        delta = Math.abs(Number(rowDebit || tx.debit || tx.net) || 0);
      }

      // Reverse effect on target Credit Card unbilled balance
      if (rowTaggedCard && delta !== 0) {
        try {
          const ccSheet = ss.getSheetByName('Credit Cards');
          if (ccSheet && ccSheet.getLastRow() >= 2) {
            const ccHeaders = ccSheet.getRange(1, 1, 1, ccSheet.getLastColumn()).getValues()[0];
            const ccCols = findCreditCardColumnIndices(ccHeaders);
            const ccValues = ccSheet.getDataRange().getValues();
            const targetName = rowTaggedCard.toLowerCase().trim();
            for (let i = 1; i < ccValues.length; i++) {
              const rowCardName = String(ccValues[i][ccCols.colCardName - 1] || '').toLowerCase().trim();
              if (rowCardName === targetName) {
                const currUnbilled = Number(ccValues[i][ccCols.colUnbilled - 1]) || 0;
                let newUnbilled = currUnbilled - delta;
                if (newUnbilled < 0) newUnbilled = 0;
                newUnbilled = Math.round(newUnbilled * 100) / 100;
                ccSheet.getRange(i + 1, ccCols.colUnbilled).setValue(newUnbilled);
                break;
              }
            }
          }
        } catch (ccErr) {
          console.warn('Error reversing credit card unbilled amount on delete: ' + ccErr);
        }
      }

      sheet.deleteRow(rowId);

      if (typeof calculateRemainders === 'function') {
        try {
          calculateRemainders();
          SpreadsheetApp.flush();
        } catch (err) {
          console.warn('calculateRemainders error: ' + err);
        }
      }

      return jsonResponse({ status: 'success', message: 'Transaction deleted successfully' });
    }

    if (action === 'updateSipStatus') {
      const sheet = ss.getSheetByName('SIP Tracer');
      if (!sheet) throw new Error('SIP Tracer sheet not found');

      const fundName = (data.fundName || '').toLowerCase().trim();
      const targetMonth = data.targetMonth || 'current';
      const status = data.status || 'Paid';
      const col = targetMonth === 'next' ? 8 : 5; // Column E = 5, Column H = 8

      const values = sheet.getDataRange().getValues();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][0]).toLowerCase().trim() === fundName) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Fund not found in SIP Tracer: ' + fundName);
      }

      sheet.getRange(matchedRow, col).setValue(status);
      return jsonResponse({ status: 'success', message: 'SIP status updated to ' + status });
    }

    if (action === 'updatePolicy') {
      const sheet = ss.getSheetByName('LIC AND TATA AIA');
      if (!sheet) throw new Error('LIC AND TATA AIA sheet not found');

      const p = data.policy || {};
      const policyNumber = String(p.policyNumber || '').trim();
      if (!policyNumber) throw new Error('Policy number is required');

      const values = sheet.getDataRange().getValues();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][0]).trim() === policyNumber) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Policy not found: ' + policyNumber);
      }

      // Only Last Paid and Due Date can be modified
      if (p.lastPaid !== undefined && p.lastPaid !== null) {
        sheet.getRange(matchedRow, 4).setValue(String(p.lastPaid).trim());
      }
      if (p.dueDate !== undefined && p.dueDate !== null) {
        sheet.getRange(matchedRow, 5).setValue(String(p.dueDate).trim());
      }
      if (p.installmentsRemaining !== undefined && p.installmentsRemaining !== null && String(p.installmentsRemaining).trim() === '0') {
        sheet.getRange(matchedRow, 7).setValue('0');
      }

      return jsonResponse({ status: 'success', message: 'Policy dates updated successfully' });
    }

    if (action === 'addPolicy') {
      const sheet = ss.getSheetByName('LIC AND TATA AIA');
      if (!sheet) throw new Error('LIC AND TATA AIA sheet not found');

      const p = data.policy || {};
      const policyNumber = String(p.policyNumber || '').trim();
      if (!policyNumber) throw new Error('Policy number is required');

      const birthDate = p.birthDate || '05/05/2000';
      const amount = Number(p.amount) || Number(p.premiumAmount) || 0;
      const lastPaid = p.lastPaid || '';
      const dueDate = p.dueDate || '';
      const lastDate = p.lastDate || p.maturityDate || ''; // Maturity Date in Last Date column
      const subCategory = p.subCategory || 'LIC Policies';
      const id = p.id || '';
      const password = p.password || '';
      const sumAssured = Number(p.sumAssured) || 0;
      const notes = p.notes || '';

      sheet.appendRow([
        policyNumber,    // Col 1: Policy Number
        birthDate,       // Col 2: Birth Date
        amount,          // Col 3: Amount
        lastPaid,        // Col 4: Last Paid
        dueDate,         // Col 5: Due Date
        lastDate,        // Col 6: Last Date (Maturity Date)
        '',              // Col 7: Installment Remains (auto-calculated by sheet formula)
        subCategory,     // Col 8: Sub Category
        '',        // Col 9: Category
        id,              // Col 10: Id
        password,        // Col 11: Password
        sumAssured,      // Col 12: Sum Assured
        notes            // Col 13: Notes / Remarks
      ]);

      return jsonResponse({ status: 'success', message: 'Policy added successfully' });
    }

    if (action === 'updateSip') {
      const sheet = ss.getSheetByName('SIP Tracer');
      if (!sheet) throw new Error('SIP Tracer sheet not found');

      const sip = data.sip || {};
      const fundName = (sip.fundName || '').toLowerCase().trim();

      const values = sheet.getDataRange().getValues();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][0]).toLowerCase().trim() === fundName) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Fund not found in SIP Tracer: ' + fundName);
      }

      if (sip.currentSip !== undefined && sip.currentSip !== null) {
        sheet.getRange(matchedRow, 3).setValue(Number(sip.currentSip) || 0);
      }
      if (sip.currentMonth !== undefined && sip.currentMonth !== null) {
        sheet.getRange(matchedRow, 4).setValue(String(sip.currentMonth).trim());
      }
      if (sip.currentStatus !== undefined && sip.currentStatus !== null) {
        sheet.getRange(matchedRow, 5).setValue(String(sip.currentStatus).trim());
      }
      if (sip.nextSip !== undefined && sip.nextSip !== null) {
        sheet.getRange(matchedRow, 6).setValue(Number(sip.nextSip) || 0);
      }
      if (sip.nextMonth !== undefined && sip.nextMonth !== null) {
        sheet.getRange(matchedRow, 7).setValue(String(sip.nextMonth).trim());
      }
      if (sip.nextStatus !== undefined && sip.nextStatus !== null) {
        sheet.getRange(matchedRow, 8).setValue(String(sip.nextStatus).trim());
      }
      if (sip.bankAccount !== undefined && sip.bankAccount !== null) {
        sheet.getRange(matchedRow, 9).setValue(String(sip.bankAccount).trim());
      }
      if (sip.platform !== undefined && sip.platform !== null) {
        sheet.getRange(matchedRow, 10).setValue(String(sip.platform).trim());
      }

      return jsonResponse({ status: 'success', message: 'SIP updated successfully' });
    }

    if (action === 'addMutualFundTransaction') {
      const sheet = ss.getSheetByName('Mutual Fund Transactions');
      if (!sheet) throw new Error('Mutual Fund Transactions sheet not found');

      const tx = data.transaction || {};
      const date = tx.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'dd/MM/yyyy');
      const schemeId = tx.schemeId || '';
      const schemeCode = tx.schemeCode || '';
      const fundCategory = tx.fundCategory || 'Equity';
      const fundSubCategory = tx.fundSubCategory || 'Mutual Fund';
      const platform = tx.platform || 'Groww';
      const subCategory = tx.subCategory || 'Mutual Fund';
      const folioNumber = tx.folioNumber || '';
      const isRedeem = tx.isRedeem === true || (tx.units && Number(tx.units) < 0);
      const units = isRedeem ? -Math.abs(Number(tx.units) || 0) : Math.abs(Number(tx.units) || 0);
      const nav = Number(tx.nav) || 0;
      const actualInvested = isRedeem ? '' : (tx.actualInvested !== undefined && tx.actualInvested !== null && tx.actualInvested !== '' ? Number(tx.actualInvested) : (Number(tx.amount) || ''));

      sheet.appendRow([
        '',              // 1: Scheme Name
        schemeId,        // 2: Scheme Id
        date,            // 3: Date
        fundCategory,    // 4: Fund Category
        fundSubCategory, // 5: Fund Sub Category
        platform,        // 6: Platform
        subCategory,     // 7: Sub Category
        folioNumber,     // 8: Folio Number
        '',              // 9: Invested / Redeemed Amount
        actualInvested,  // 10: Actual Invested
        '',              // 11: Cost
        units,           // 12: Units
        nav,             // 13: Buy NAV/Redeem NAV
        nav,             // 14: Current NAV
        '',              // 15: P&L
        '',              // 16: Days Invested
        '',              // 17: Processed at sheet
        schemeCode       // 18: Scheme Code
      ]);

      return jsonResponse({ status: 'success', message: 'Mutual Fund transaction recorded successfully' });
    }

    if (action === 'addHolding') {
      const sheet = ss.getSheetByName('Mutual Investment Holdings');
      if (!sheet) throw new Error('Mutual Investment Holdings sheet not found');

      const h = data.holding || {};
      const schemeName = h.schemeName || '';
      const schemeCode = h.schemeCode || '';
      const schemeId = h.schemeId || '';
      const category = h.category || 'Equity';
      const subCategory = h.subCategory || 'Small Cap';
      const savingsType = h.savingsType || 'Mutual Fund';
      const savingsCategory = h.savingsCategory || 'Savings';
      const source = h.source || 'Groww';
      const units = Number(h.units) || 0;
      const avgBuyingNav = Number(h.avgBuyingNav) || 0;
      const investedValue = Number(h.investedValue) || (units * avgBuyingNav);
      const nav = Number(h.nav) || avgBuyingNav;
      const navDate = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'dd/MM/yyyy');
      const currentValue = Number(h.currentValue) || (units * nav);

      sheet.appendRow([
        schemeName, schemeCode, schemeId, category, subCategory, savingsType,
        savingsCategory, source, units, avgBuyingNav, investedValue, nav,
        navDate, currentValue, 0, 0, 0, investedValue, 0
      ]);

      return jsonResponse({ status: 'success', message: 'Mutual Fund holding added successfully' });
    }

    if (action === 'addCreditCard') {
      const sheet = ss.getSheetByName('Credit Cards');
      if (!sheet) throw new Error('Credit Cards sheet not found');

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is mandatory');

      const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      const cols = findCreditCardColumnIndices(headers);
      const values = sheet.getDataRange().getValues();

      // Check uniqueness
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][cols.colCardName - 1] || '').toLowerCase().trim() === cardName.toLowerCase()) {
          throw new Error('A credit card named "' + cardName + '" already exists');
        }
      }

      const billedAmount = Number(c.billedAmount) || 0;
      const unbilledAmount = 0; // Unbilled amount not taken during creation, defaults to 0
      const dueDate = String(c.dueDate || '').trim();
      const lastPaid = '';
      const status = billedAmount > 0 ? 'Unpaid' : 'Paid';

      const newRow = new Array(headers.length).fill('');
      newRow[cols.colCardName - 1] = cardName;
      newRow[cols.colBilled - 1] = billedAmount;
      newRow[cols.colUnbilled - 1] = unbilledAmount;
      newRow[cols.colDueDate - 1] = dueDate;
      if (cols.colLastPaid > 0) newRow[cols.colLastPaid - 1] = lastPaid;
      if (cols.colStatus > 0) newRow[cols.colStatus - 1] = status;

      sheet.appendRow(newRow);
      SpreadsheetApp.flush();

      return jsonResponse({
        status: 'success',
        message: 'Credit card ' + cardName + ' added successfully',
        card: {
          id: sheet.getLastRow() - 1,
          cardName: cardName,
          billedAmount: billedAmount,
          unbilledAmount: unbilledAmount,
          dueDate: dueDate,
          lastPaid: lastPaid,
          status: status
        }
      });
    }

    if (action === 'updateCreditCard') {
      const sheet = ss.getSheetByName('Credit Cards');
      if (!sheet) throw new Error('Credit Cards sheet not found');

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is required');

      const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      const cols = findCreditCardColumnIndices(headers);
      const values = sheet.getDataRange().getValues();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][cols.colCardName - 1] || '').toLowerCase().trim() === cardName.toLowerCase()) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Credit Card not found: ' + cardName);
      }

      // User constraint: from the update option user CANNOT mark it as paid.
      // Only Due Date and Billed Amount can be updated.
      if (c.dueDate !== undefined && c.dueDate !== null) {
        sheet.getRange(matchedRow, cols.colDueDate).setValue(String(c.dueDate).trim());
      }
      if (c.billedAmount !== undefined && c.billedAmount !== null) {
        const bAmt = Number(c.billedAmount) || 0;
        sheet.getRange(matchedRow, cols.colBilled).setValue(bAmt);
        if (bAmt > 0 && cols.colStatus > 0) {
          sheet.getRange(matchedRow, cols.colStatus).setValue('Unpaid');
        }
      }

      SpreadsheetApp.flush();
      return jsonResponse({ status: 'success', message: 'Credit card details updated successfully' });
    }

    if (action === 'markCreditCardPaid') {
      const sheet = ss.getSheetByName('Credit Cards');
      if (!sheet) throw new Error('Credit Cards sheet not found');

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is required');

      const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      const cols = findCreditCardColumnIndices(headers);
      const values = sheet.getDataRange().getValues();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][cols.colCardName - 1] || '').toLowerCase().trim() === cardName.toLowerCase()) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Credit Card not found: ' + cardName);
      }

      const todayStr = Utilities.formatDate(new Date(), 'Asia/Kolkata', 'dd/MM/yyyy');
      // Set billed amount to 0
      sheet.getRange(matchedRow, cols.colBilled).setValue(0);
      
      // Advance due date by +1 month automatically
      let currDueDate = values[matchedRow - 1][cols.colDueDate - 1];
      if (currDueDate instanceof Date) {
        currDueDate = Utilities.formatDate(currDueDate, 'Asia/Kolkata', 'dd/MM/yyyy');
      } else {
        currDueDate = String(currDueDate || '').trim();
      }
      const nextDueDate = advanceDateByOneMonth(currDueDate);
      if (nextDueDate) {
        sheet.getRange(matchedRow, cols.colDueDate).setValue(nextDueDate);
      }

      if (cols.colLastPaid > 0) {
        sheet.getRange(matchedRow, cols.colLastPaid).setValue(todayStr);
      }
      if (cols.colStatus > 0) {
        sheet.getRange(matchedRow, cols.colStatus).setValue('Paid');
      }

      SpreadsheetApp.flush();
      return jsonResponse({ 
        status: 'success', 
        message: cardName + ' marked as paid! Next due date moved 1 month to ' + nextDueDate,
        nextDueDate: nextDueDate
      });
    }

    if (action === 'markCreditCardBillGenerated') {
      const sheet = ss.getSheetByName('Credit Cards');
      if (!sheet) throw new Error('Credit Cards sheet not found');

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is required');

      const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      const cols = findCreditCardColumnIndices(headers);
      const values = sheet.getDataRange().getValues();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][cols.colCardName - 1] || '').toLowerCase().trim() === cardName.toLowerCase()) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Credit Card not found: ' + cardName);
      }

      // Read current unbilled amount
      let unbilledVal = Number(values[matchedRow - 1][cols.colUnbilled - 1]) || 0;
      if (c.unbilledAmount !== undefined && Number(c.unbilledAmount) > 0 && unbilledVal === 0) {
        unbilledVal = Number(c.unbilledAmount);
      }

      // Set billed amount = unbilledVal, unbilled amount = 0
      sheet.getRange(matchedRow, cols.colBilled).setValue(unbilledVal);
      sheet.getRange(matchedRow, cols.colUnbilled).setValue(0);

      if (cols.colStatus > 0) {
        sheet.getRange(matchedRow, cols.colStatus).setValue(unbilledVal > 0 ? 'Unpaid' : 'Paid');
      }

      SpreadsheetApp.flush();
      return jsonResponse({
        status: 'success',
        message: cardName + ' bill generated: ₹' + unbilledVal + ' billed, unbilled reset to ₹0',
        billedAmount: unbilledVal,
        unbilledAmount: 0
      });
    }

    return jsonResponse({ status: 'error', message: 'Unsupported POST action' });
  } catch (err) {
    return jsonResponse({ status: 'error', message: err.toString() });
  }
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// Helpers
function getSheetValues(ss, sheetName) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) return [];
  const range = sheet.getDataRange();
  const values = range.getValues();
  if (values.length < 2) return [];

  const tz = 'Asia/Kolkata';
  const headers = values[0];
  const results = [];
  for (let i = 1; i < values.length; i++) {
    const row = values[i];
    if (row.every(cell => cell === '' || cell === null)) continue;
    const obj = {};
    headers.forEach((h, index) => {
      if (h) {
        let val = row[index];
        if (val instanceof Date) {
          val = Utilities.formatDate(val, tz, 'dd/MM/yyyy');
        }
        obj[h.toString().trim()] = val;
      }
    });
    results.push(obj);
  }
  return results;
}

function getTransactions(ss, limit, page) {
  const sheet = ss.getSheetByName('Transactions');
  if (!sheet) return [];
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return [];

  let startRow = 2;
  let numRows = lastRow - 1;
  if (limit && Number(limit) > 0) {
    startRow = Math.max(2, lastRow - Number(limit) + 1);
    numRows = lastRow - startRow + 1;
  }
  const lastCol = sheet.getLastColumn();
  const transHeaders = sheet.getRange(1, 1, 1, Math.max(15, lastCol)).getValues()[0];
  const cols = findTransactionsColumnIndices(transHeaders);
  const maxCol = Math.max(
    lastCol,
    cols.colDate, cols.colCredit, cols.colDebit, cols.colNet,
    cols.colRemainder, cols.colSubCategory, cols.colType, cols.colLoans,
    cols.colAccount, cols.colCategory, cols.colYear, cols.colMonth,
    cols.colDebitWithoutSign, cols.colProcessed, cols.colTaggedCard
  );
  const values = sheet.getRange(startRow, 1, numRows, maxCol).getValues();
  const tz = 'Asia/Kolkata';

  // Read credit cards list for single-card fallback
  let creditCardNamesList = [];
  try {
    const ccSheet = ss.getSheetByName('Credit Cards');
    if (ccSheet && ccSheet.getLastRow() >= 2) {
      const ccHeaders = ccSheet.getRange(1, 1, 1, ccSheet.getLastColumn()).getValues()[0];
      const ccCols = findCreditCardColumnIndices(ccHeaders);
      const ccVals = ccSheet.getDataRange().getValues();
      for (let cIdx = 1; cIdx < ccVals.length; cIdx++) {
        const cName = String(ccVals[cIdx][ccCols.colCardName - 1] || '').trim();
        if (cName) creditCardNamesList.push(cName);
      }
    }
  } catch (ignored) {}

  return values.map((row, idx) => {
    let dateVal = cols.colDate > 0 ? row[cols.colDate - 1] : '';
    if (dateVal instanceof Date) {
      dateVal = Utilities.formatDate(dateVal, tz, 'dd/MM/yyyy');
    } else if (dateVal !== null && dateVal !== undefined) {
      dateVal = String(dateVal).trim();
    } else {
      dateVal = '';
    }

    let credit = cols.colCredit > 0 ? (Number(row[cols.colCredit - 1]) || 0) : 0;
    let debit = cols.colDebit > 0 ? (Number(row[cols.colDebit - 1]) || 0) : 0;
    let net = cols.colNet > 0 ? (Number(row[cols.colNet - 1]) || 0) : 0;
    let remainder = cols.colRemainder > 0 ? (Number(row[cols.colRemainder - 1]) || 0) : 0;
    let dws = cols.colDebitWithoutSign > 0 ? (Number(row[cols.colDebitWithoutSign - 1]) || 0) : 0;

    // Ensure robust debit and net consistency
    if (debit === 0 && dws > 0) {
      debit = -dws;
    } else if (debit !== 0 && dws === 0) {
      dws = Math.abs(debit);
    }
    if (net === 0 && (credit > 0 || debit !== 0 || dws > 0)) {
      net = credit + (debit !== 0 ? debit : -dws);
    }

    let year = cols.colYear > 0 ? (Number(row[cols.colYear - 1]) || 0) : 0;
    let month = cols.colMonth > 0 ? (Number(row[cols.colMonth - 1]) || 0) : 0;
    if (year === 0 || month === 0) {
      if (dateVal && dateVal.includes('/')) {
        const dParts = dateVal.split('/');
        if (dParts.length === 3) {
          if (year === 0) year = Number(dParts[2]) || new Date().getFullYear();
          if (month === 0) month = Number(dParts[1]) || (new Date().getMonth() + 1);
        }
      }
    }

    const loanVal = cols.colLoans > 0 ? String(row[cols.colLoans - 1] || '').trim() : '';
    const subCatVal = cols.colSubCategory > 0 ? String(row[cols.colSubCategory - 1] || '').trim() : '';
    const typeVal = cols.colType > 0 ? String(row[cols.colType - 1] || '').trim() : 'Expense';
    const accVal = cols.colAccount > 0 ? String(row[cols.colAccount - 1] || '').trim() : '';
    const catVal = cols.colCategory > 0 ? String(row[cols.colCategory - 1] || '').trim() : '';
    const procVal = cols.colProcessed > 0 ? String(row[cols.colProcessed - 1] || '').trim() : '';

    return {
      id: startRow + idx,
      date: dateVal,
      credit: credit,
      debit: debit,
      net: net,
      remainder: remainder,
      subCategory: subCatVal,
      type: typeVal || 'Expense',
      loans: loanVal,
      account: accVal,
      category: catVal,
      year: year,
      month: month,
      debitWithoutSign: dws,
      scannedForBudgetAnalysis: procVal,
      taggedCreditCard: (() => {
        let card = (cols.colTaggedCard > 0 && row.length >= cols.colTaggedCard && row[cols.colTaggedCard - 1]) ? String(row[cols.colTaggedCard - 1]).trim() : '';
        if (!card && loanVal.toUpperCase() === 'CREDIT CARD' && creditCardNamesList.length === 1) {
          card = creditCardNamesList[0];
        }
        return card;
      })(),
      isProcessed: procVal === 'Done' || (procVal !== '' && procVal !== null && procVal !== undefined)
    };
  }).reverse();
}

function getAccounts(ss) {
  const raw = getSheetValues(ss, 'Current Account Balance');
  return raw.map(a => ({
    name: a['Accounts'],
    currentBalance: Number(a['Current Balance']) || 0
  }));
}

function getLoans(ss) {
  const raw = getSheetValues(ss, 'Current Loan OutStanding');
  return raw.map(l => ({
    name: l['Loans'],
    outstanding: Number(l['Current OutStanding']) || 0,
    interestRate: Number(l['Interest Rate']) || 0,
    isDebt: String(l['IsDebt']).toUpperCase() === 'TRUE'
  }));
}

function getExpenseTracker(ss) {
  const raw = getSheetValues(ss, 'Expense Tracker');
  return raw.map(e => ({
    expenseType: e['Expense Type'] || '',
    subCategory: e['Sub-Category'] || '',
    amountToBePaid: Number(e['Amount To Be Paid']) || 0,
    amount: Number(e['Amount']) || 0,
    year: Number(e['Year']) || 0,
    month: Number(e['Month']) || 0,
    percent: Number(e['Percent']) || 0,
    previousMonthCost: Number(e['Previouse Months Cost'] !== undefined ? e['Previouse Months Cost'] : (e['Previous Months Cost'] || 0)) || 0,
    deviation: Number(e['Deviation']) || 0,
    currentMonthCost: Number(e['Current Month Costing']) || 0
  }));
}

function getBudget(ss) {
  const raw = getSheetValues(ss, 'Budget');
  return raw.map(b => ({
    year: b['Year'],
    month: b['Month'],
    monthNumber: b['Month Number'],
    budget: Number(b['BUDGET']) || 0,
    spend: Number(b['Spend']) || 0,
    remainder: Number(b['Remainder']) || 0,
    canSpendPerDay: Number(b['Can Spend Per Day']) || 0,
    investment: Number(b['Investment']) || 0
  }));
}

function getGoals(ss) {
  const raw = getSheetValues(ss, 'Goals');
  return raw.map(g => {
    const target = Number(g['Goal Amount']) || 0;
    const current = Number(g['Occured Till Date']) || 0;
    return {
      name: g['Goals Name'],
      targetAmount: target,
      currentAmount: current,
      achievedPercent: target > 0 ? Math.round((current / target) * 1000) / 10 : 0,
      savingAllocationPercent: (Number(g['Percentage Of saving To Put']) || 0) * 100
    };
  });
}

function getHoldings(ss) {
  const raw = getSheetValues(ss, 'Mutual Investment Holdings');
  return raw.map(h => {
    const invested = Number(h['Invested Value']) || 0;
    const current = Number(h['Current Value']) || 0;
    const prev = Number(h['Previous Value']) || current;
    const oneDay = Number(h['1D Change']) || (current - prev);
    const oneDayPct = prev > 0 ? (oneDay / prev) * 100 : 0;
    const returns = Number(h['Returns']) || (current - invested);
    const absReturn = Number(h['ABS Return']) || (invested > 0 ? (returns / invested) * 100 : 0);
    const afterTax = Number(h['After tax Return Approx']) || (returns * 0.8);

    return {
      schemeName: h['Scheme Name'],
      schemeCode: h['Scheme Code'],
      schemeId: h['Scheme Id'],
      category: h['Category'],
      subCategory: h['Sub-category'],
      savingsType: h['Savings Type'],
      savingsCategory: h['Savings Catgory'],
      source: h['Source'] || 'Groww',
      units: Number(h['Units']) || 0,
      avgBuyingNav: Number(h['Avg Buying Nav']) || 0,
      investedValue: invested,
      nav: Number(h['NAV']) || 0,
      navDate: h['NAV Date'],
      currentValue: current,
      returns: returns,
      absReturn: absReturn,
      afterTaxReturn: afterTax,
      previousValue: prev,
      oneDayChange: oneDay,
      oneDayChangePercent: oneDayPct
    };
  });
}

function getMutualFundTransactions(ss, limit) {
  const raw = getSheetValues(ss, 'Mutual Fund Transactions');
  const items = (limit && Number(limit) > 0) ? raw.slice(-Number(limit)) : raw;
  return items.map((t, idx) => ({
    id: idx + 1,
    schemeName: t['Scheme Name'],
    schemeId: t['Scheme Id'],
    schemeCode: t['Scheme Code'],
    date: t['Date'],
    fundCategory: t['Fund Category'],
    fundSubCategory: t['Fund Sub Category'],
    subCategory: t['Sub Category'],
    platform: t['Platform'] || 'Groww',
    folioNumber: t['Folio Number'],
    amount: Number(t['Invested / Redeemed Amount']) || 0,
    units: Number(t['Units']) || 0,
    nav: Number(t['Buy NAV/Reedem NAV']) || 0,
    currentNav: Number(t['Current NAV']) || 0,
    pnl: Number(t['P&L']) || 0,
    daysInvested: Number(t['Days Invested']) || 0,
    processed: t['Processed'] || 'Done'
  })).reverse();
}

function getInsurance(ss) {
  const raw = getSheetValues(ss, 'LIC AND TATA AIA');
  return raw.map(p => ({
    policyNumber: String(p['Policy Number'] || '').trim(),
    birthDate: p['Birth Date'] || '',
    premiumAmount: Number(p['Amount '] || p['Amount']) || 0,
    lastPaid: p['Last Paid'] || '',
    dueDate: p['Due Date'] || '',
    lastDate: p['Last Date'] || '',
    installmentsRemaining: String(p['Installment Remains'] || ''),
    subCategory: p['Sub Category'] || '',
    category: p['Category'] || '',
    id: String(p['Id'] || ''),
    password: String(p['Password'] || ''),
    sumAssured: Number(p['Sum Assured']) || 0,
    notes: p['Status / Notes'] || p['Notes'] || ''
  }));
}

function getSips(ss) {
  const raw = getSheetValues(ss, 'SIP Tracer');
  return raw.map(s => ({
    fundName: s['Fund Name'],
    role: s['Role in Architecture'],
    currentSip: Number(s["This Month's SIP"]) || 0,
    currentMonth: s['This Month'] || '',
    currentStatus: s['Payment Status'] || 'Not Paid',
    nextSip: Number(s['Next SIP (Final)']) || 0,
    nextMonth: s['Next Month'] || '',
    nextStatus: s["Next Month's Payment Status"] || 'Not Paid',
    bankAccount: s['Linked Bank Account'] || '',
    platform: s['Platform'] || ''
  }));
}

function getCategories(ss) {
  const raw = getSheetValues(ss, 'Category Tables');
  const catMap = {};
  raw.forEach(r => {
    const cat = r['Category Name'];
    const sub = r['SubCategory'];
    if (!cat || !sub) return;
    if (!catMap[cat]) catMap[cat] = [];
    catMap[cat].push(sub);
  });
  return Object.keys(catMap).map(k => ({ name: k, subCategories: catMap[k] }));
}


/**
 * Standalone function to calculate running balance (Remainder) in Column E.
 * Fetches opening balances from both 'Account Opening Balance' and 'Loans Opening Balance',
 * calculates chronological running balances per account/loan, and batch-updates Column E.
 */
function calculateRemainders() {
  SpreadsheetApp.flush();
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var transSheet = ss.getSheetByName('Transactions');
  if (!transSheet) {
    Logger.log("Error: 'Transactions' sheet not found.");
    return;
  }

  // 1. Load Opening Balances for Accounts
  var accBalances = {};
  var accSheet = ss.getSheetByName('Account Opening Balance');
  if (accSheet) {
    var accData = accSheet.getDataRange().getValues();
    for (var i = 1; i < accData.length; i++) {
      var accName = accData[i][0] ? String(accData[i][0]).trim() : "";
      if (accName !== "") {
        accBalances[accName] = Number(accData[i][1]) || 0;
      }
    }
  }

  // 2. Load Opening Balances for Loans
  var loanBalances = {};
  var loanSheet = ss.getSheetByName('Loans Opening Balance');
  if (loanSheet) {
    var loanData = loanSheet.getDataRange().getValues();
    for (var i = 1; i < loanData.length; i++) {
      var loanName = loanData[i][0] ? String(loanData[i][0]).trim() : "";
      if (loanName !== "") {
        loanBalances[loanName] = Number(loanData[i][1]) || 0;
      }
    }
  }

  var lastRow = transSheet.getLastRow();
  if (lastRow < 2) return; // No transaction rows

  var numRows = lastRow - 1; // Exclude header row

  var lastCol = transSheet.getLastColumn();
  var transHeaders = transSheet.getRange(1, 1, 1, Math.max(15, lastCol)).getValues()[0];
  var cols = findTransactionsColumnIndices(transHeaders);
  var maxReadCol = Math.max(
    lastCol,
    cols.colCredit,
    cols.colDebit,
    cols.colLoans,
    cols.colAccount,
    cols.colDebitWithoutSign,
    cols.colRemainder
  );

  // Read all needed columns dynamically
  var transData = transSheet.getRange(2, 1, numRows, maxReadCol).getValues();

  var runningBalances = {};
  var remainderOutput = [];

  // Calculate running balances in-memory
  for (var i = 0; i < numRows; i++) {
    var row = transData[i];
    var credit = cols.colCredit > 0 ? (Number(row[cols.colCredit - 1]) || 0) : 0;
    var debit = cols.colDebit > 0 ? (Number(row[cols.colDebit - 1]) || 0) : 0;
    var loan = cols.colLoans > 0 && row[cols.colLoans - 1] ? String(row[cols.colLoans - 1]).trim() : "";
    var account = cols.colAccount > 0 && row[cols.colAccount - 1] ? String(row[cols.colAccount - 1]).trim() : "";
    var dws = cols.colDebitWithoutSign > 0 ? (Number(row[cols.colDebitWithoutSign - 1]) || 0) : 0;

    // CRITICAL FIX: If debit is 0, but dws is non-zero, debit is -Math.abs(dws)
    if (debit === 0 && dws !== 0) {
      debit = -Math.abs(dws);
    }

    var entityKey = account !== "" ? account : loan;
    var currentBalance = 0;

    if (entityKey !== "") {
      if (!(entityKey in runningBalances)) {
        if (account !== "") {
          runningBalances[entityKey] = (account in accBalances) ? accBalances[account] : 0;
        } else {
          runningBalances[entityKey] = (loan in loanBalances) ? loanBalances[loan] : 0;
        }
      }

      // Cumulative running balance = previous balance + Credit + Debit (Debit is already negative)
      runningBalances[entityKey] += (credit + debit);
      currentBalance = Math.round(runningBalances[entityKey] * 100) / 100;
    }

    remainderOutput.push([currentBalance]);
  }

  // Batch-write directly into dynamic colRemainder (starting at row 2)
  if (cols.colRemainder > 0) {
    transSheet.getRange(2, cols.colRemainder, remainderOutput.length, 1).setValues(remainderOutput);
  }
  SpreadsheetApp.flush();
}

function advanceDateByOneMonth(dateStr) {
  if (!dateStr || typeof dateStr !== 'string') return dateStr;
  var parts = dateStr.trim().split('/');
  if (parts.length === 3) {
    var day = parseInt(parts[0], 10);
    var month = parseInt(parts[1], 10); // 1-12
    var year = parseInt(parts[2], 10);

    // Advance 1 month
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }

    // Adjust day for months with fewer days (e.g. Feb)
    var maxDaysInMonth = new Date(year, month, 0).getDate();
    if (day > maxDaysInMonth) {
      day = maxDaysInMonth;
    }

    var dd = day < 10 ? '0' + day : '' + day;
    var mm = month < 10 ? '0' + month : '' + month;
    return dd + '/' + mm + '/' + year;
  }
  return dateStr;
}

function findTransactionsTaggedCardColumn(headers) {
  return findTransactionsColumnIndices(headers).colTaggedCard;
}

function findTransactionsColumnIndices(headers) {
  let colDate = -1;
  let colCredit = -1;
  let colDebit = -1;
  let colNet = -1;
  let colRemainder = -1;
  let colSubCategory = -1;
  let colType = -1;
  let colLoans = -1;
  let colAccount = -1;
  let colCategory = -1;
  let colYear = -1;
  let colMonth = -1;
  let colDebitWithoutSign = -1;
  let colProcessed = -1;
  let colTaggedCard = -1;

  if (headers && headers.length) {
    for (let c = 0; c < headers.length; c++) {
      const h = String(headers[c] || '').toLowerCase().trim();
      if (!h) continue;
      const idx = c + 1;

      // 1. Tagged Credit Card (check before generic Credit or Card)
      if (colTaggedCard === -1 && (
        h.includes('tagged') ||
        h === 'credit card' ||
        h === 'credit_card' ||
        h === 'card' ||
        (h.includes('credit') && h.includes('card'))
      )) {
        colTaggedCard = idx;
      }
      // 2. Debit Without Sign (check before generic Debit)
      else if (colDebitWithoutSign === -1 && (
        (h.includes('debit') && (h.includes('without') || h.includes('sign') || h.includes('w/o') || h.includes('abs') || h.includes('no') || h.includes('pos'))) ||
        h === 'dws'
      )) {
        colDebitWithoutSign = idx;
      }
      // 3. Sub Category (check before generic Category)
      else if (colSubCategory === -1 && (
        h.includes('sub') ||
        h.includes('subcategory') ||
        h.includes('sub category') ||
        h.includes('sub-cat')
      )) {
        colSubCategory = idx;
      }
      // 4. Remainder / Running balance
      else if (colRemainder === -1 && (
        h.includes('remainder') ||
        h.includes('running') ||
        (h.includes('balance') && !h.includes('account') && !h.includes('loan')) ||
        h.includes('cumulative')
      )) {
        colRemainder = idx;
      }
      // 5. Debit (check after Debit Without Sign)
      else if (colDebit === -1 && (
        (h.includes('debit') && !h.includes('without') && !h.includes('sign') && !h.includes('w/o')) ||
        h === 'dr' ||
        h.includes('dr ') ||
        h.includes('dr amount') ||
        h === 'outflow'
      )) {
        colDebit = idx;
      }
      // 6. Credit (check after Tagged Credit Card)
      else if (colCredit === -1 && (
        (h.includes('credit') && !h.includes('card')) ||
        h === 'cr' ||
        h.includes('cr ') ||
        h.includes('cr amount') ||
        h === 'inflow' ||
        h === 'deposit'
      )) {
        colCredit = idx;
      }
      // 7. Net
      else if (colNet === -1 && (
        h.includes('net') ||
        (h.includes('amount') && !h.includes('credit') && !h.includes('debit') && !h.includes('bill') && !h.includes('cr') && !h.includes('dr'))
      )) {
        colNet = idx;
      }
      // 8. Loans
      else if (colLoans === -1 && (h.includes('loan') || h.includes('liability'))) {
        colLoans = idx;
      }
      // 9. Type
      else if (colType === -1 && h.includes('type')) {
        colType = idx;
      }
      // 10. Account (check after Loans to avoid Loan Account matching Account)
      else if (colAccount === -1 && (
        h.includes('account') ||
        h.includes('bank') ||
        h.includes('source') ||
        h.includes('payment mode')
      ) && !h.includes('loan')) {
        colAccount = idx;
      }
      // 11. Category (check after Sub Category)
      else if (colCategory === -1 && (h.includes('category') || h.includes('cat')) && !h.includes('sub')) {
        colCategory = idx;
      }
      // 12. Year
      else if (colYear === -1 && (h.includes('year') || h === 'yr' || h === 'yyyy')) {
        colYear = idx;
      }
      // 13. Month
      else if (colMonth === -1 && (h.includes('month') || h === 'mo' || h === 'mm')) {
        colMonth = idx;
      }
      // 14. Date
      else if (colDate === -1 && h.includes('date')) {
        colDate = idx;
      }
      // 15. Scanned / Processed
      else if (colProcessed === -1 && (
        h.includes('scanned') ||
        h.includes('budget analysis') ||
        h.includes('process') ||
        h.includes('status')
      )) {
        colProcessed = idx;
      }
    }
  }

  // Fallback defaults
  if (colDate === -1) colDate = 1;
  if (colCredit === -1) colCredit = 2;
  if (colDebit === -1) colDebit = 3;
  if (colNet === -1) colNet = 4;
  if (colRemainder === -1) colRemainder = 5;
  if (colSubCategory === -1) colSubCategory = 6;
  if (colType === -1) colType = 7;
  if (colLoans === -1) colLoans = 8;
  if (colAccount === -1) colAccount = 9;
  if (colCategory === -1) colCategory = 10;
  if (colYear === -1) colYear = 11;
  if (colMonth === -1) colMonth = 12;
  if (colDebitWithoutSign === -1) colDebitWithoutSign = 13;
  if (colProcessed === -1) colProcessed = 14;
  if (colTaggedCard === -1) colTaggedCard = 15;

  return {
    colDate,
    colCredit,
    colDebit,
    colNet,
    colRemainder,
    colSubCategory,
    colType,
    colLoans,
    colAccount,
    colCategory,
    colYear,
    colMonth,
    colDebitWithoutSign,
    colProcessed,
    colTaggedCard
  };
}

function findCreditCardColumnIndices(headers) {
  let colCardName = -1;
  let colBilled = -1;
  let colUnbilled = -1;
  let colDueDate = -1;
  let colLastPaid = -1;
  let colStatus = -1;

  for (let c = 0; c < headers.length; c++) {
    const h = String(headers[c] || '').toLowerCase().trim();
    if (colUnbilled === -1 && (h.includes('unbill') || h.includes('un-bill'))) {
      colUnbilled = c + 1;
    } else if (colBilled === -1 && (h.includes('bill') || h.includes('due amount'))) {
      colBilled = c + 1;
    } else if (colDueDate === -1 && h.includes('due')) {
      colDueDate = c + 1;
    } else if (colLastPaid === -1 && (h.includes('paid date') || h.includes('last paid'))) {
      colLastPaid = c + 1;
    } else if (colCardName === -1 && (h.includes('card') || h.includes('name') || h.includes('option'))) {
      colCardName = c + 1;
    } else if (colStatus === -1 && h.includes('status')) {
      colStatus = c + 1;
    }
  }

  if (colCardName === -1) colCardName = 1;
  if (colBilled === -1) colBilled = 2;
  if (colUnbilled === -1) colUnbilled = 3;
  if (colDueDate === -1) colDueDate = 4;
  if (colLastPaid === -1) colLastPaid = 5;
  if (colStatus === -1) colStatus = 6;

  return { colCardName, colBilled, colUnbilled, colDueDate, colLastPaid, colStatus };
}

function getCreditCards(ss) {
  const sheet = ss.getSheetByName('Credit Cards');
  if (!sheet) return [];
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return [];

  const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  const cols = findCreditCardColumnIndices(headers);
  const values = sheet.getDataRange().getValues();
  const tz = 'Asia/Kolkata';
  const results = [];

  for (let i = 1; i < values.length; i++) {
    const row = values[i];
    if (row.every(cell => cell === '' || cell === null)) continue;

    const cardName = String(row[cols.colCardName - 1] || '').trim();
    if (!cardName) continue;

    const billedAmount = Number(row[cols.colBilled - 1]) || 0;
    const unbilledAmount = Number(row[cols.colUnbilled - 1]) || 0;

    let dueDateVal = row[cols.colDueDate - 1];
    if (dueDateVal instanceof Date) {
      dueDateVal = Utilities.formatDate(dueDateVal, tz, 'dd/MM/yyyy');
    } else {
      dueDateVal = String(dueDateVal || '').trim();
    }

    let lastPaidVal = cols.colLastPaid > 0 ? row[cols.colLastPaid - 1] : '';
    if (lastPaidVal instanceof Date) {
      lastPaidVal = Utilities.formatDate(lastPaidVal, tz, 'dd/MM/yyyy');
    } else {
      lastPaidVal = String(lastPaidVal || '').trim();
    }

    let statusVal = cols.colStatus > 0 ? String(row[cols.colStatus - 1] || '').trim() : '';
    if (!statusVal) {
      statusVal = billedAmount <= 0 ? 'Paid' : 'Unpaid';
    }

    results.push({
      id: i,
      cardName: cardName,
      billedAmount: billedAmount,
      unbilledAmount: unbilledAmount,
      dueDate: dueDateVal,
      lastPaid: lastPaidVal,
      status: statusVal
    });
  }
  return results;
}
