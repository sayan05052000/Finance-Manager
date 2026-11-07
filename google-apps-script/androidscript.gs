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

    if (action === 'all') {
      return jsonResponse({
        status: 'success',
        data: {
          accounts: getAccounts(ss),
          loans: getLoans(ss),
          budget: getBudget(ss),
          goals: getGoals(ss),
          holdings: getHoldings(ss),
          mutualFundTransactions: getMutualFundTransactions(ss, 300),
          insurance: getInsurance(ss),
          sips: getSips(ss),
          categories: getCategories(ss),
          expenseTracker: getExpenseTracker(ss),
          transactions: getTransactions(ss, 300)
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
      const credit = (tx.credit !== undefined && tx.credit !== null && tx.credit !== '' && Number(tx.credit) !== 0) ? Number(tx.credit) : '';
      const debitVal = tx.debitWithoutSign !== undefined && tx.debitWithoutSign !== null && tx.debitWithoutSign !== '' ? tx.debitWithoutSign : tx.debit;
      const debitWithoutSign = (debitVal !== undefined && debitVal !== null && debitVal !== '' && Number(debitVal) !== 0) ? Number(debitVal) : '';
      const debit = (tx.debit !== undefined && tx.debit !== null && tx.debit !== '' && Number(tx.debit) !== 0)
          ? Number(tx.debit)
          : (debitWithoutSign !== '' ? -debitWithoutSign : '');
      const net = '';
      const remainder = '';
      const subCategory = tx.subCategory || '';
      const type = tx.type || 'Expense';
      const loans = tx.loans || '';
      const account = tx.account || '';
      const category = '';
      const parts = String(date).split('/');
      const year = tx.year || (parts.length === 3 ? parts[2] : new Date().getFullYear());
      const month = tx.month || (parts.length >= 2 ? parts[1] : (new Date().getMonth() + 1));

      sheet.appendRow([
        date,
        credit,
        '',
        net,
        remainder,
        subCategory,
        type,
        loans,
        account,
        category,
        year,
        month,
        debitWithoutSign,
        ''
      ]);

      return jsonResponse({ status: 'success', message: 'Transaction added successfully' });
    }

    if (action === 'updateTransaction') {
      const sheet = ss.getSheetByName('Transactions');
      if (!sheet) throw new Error('Transactions sheet not found');

      const tx = data.transaction || {};
      const rowId = Number(tx.id);
      if (!rowId || rowId < 2 || rowId > sheet.getLastRow()) {
        throw new Error('Invalid transaction row ID: ' + rowId);
      }

      // Col 1: Date
      if (tx.date !== undefined && tx.date !== null && String(tx.date).trim() !== '') {
        const dStr = String(tx.date).trim();
        sheet.getRange(rowId, 1).setValue(dStr);
        const parts = dStr.split('/');
        if (parts.length === 3) {
          sheet.getRange(rowId, 11).setValue(parts[2]); // Col 11: Year
          sheet.getRange(rowId, 12).setValue(parts[1]); // Col 12: Month
        }
      }

      // Col 2: Credit
      if (tx.credit !== undefined && tx.credit !== null) {
        const creditVal = Number(tx.credit);
        sheet.getRange(rowId, 2).setValue(creditVal > 0 ? creditVal : '');
      }

      // Col 3: Debit (with sign)
      if (tx.debit !== undefined && tx.debit !== null) {
        const debitVal = Number(tx.debit);
        sheet.getRange(rowId, 3).setValue(debitVal !== 0 ? debitVal : '');
      }

      // Col 6: Sub Category
      if (tx.subCategory !== undefined && tx.subCategory !== null) {
        sheet.getRange(rowId, 6).setValue(String(tx.subCategory).trim());
      }

      // Col 7: Type
      if (tx.type !== undefined && tx.type !== null) {
        sheet.getRange(rowId, 7).setValue(String(tx.type).trim());
      }

      // Col 8: Loans
      if (tx.loans !== undefined) {
        sheet.getRange(rowId, 8).setValue(tx.loans ? String(tx.loans).trim() : '');
      }

      // Col 9: Account
      if (tx.account !== undefined) {
        sheet.getRange(rowId, 9).setValue(tx.account ? String(tx.account).trim() : '');
      }

      // Col 10: Category
      if (tx.category !== undefined && tx.category !== null && String(tx.category).trim() !== '') {
        sheet.getRange(rowId, 10).setValue(String(tx.category).trim());
      }

      // Col 13: Debit Without Sign
      if (tx.debitWithoutSign !== undefined && tx.debitWithoutSign !== null) {
        const dwsVal = Number(tx.debitWithoutSign);
        sheet.getRange(rowId, 13).setValue(dwsVal > 0 ? dwsVal : '');
      }

      // Col 14: Description
      if (tx.description !== undefined && tx.description !== null) {
        sheet.getRange(rowId, 14).setValue(String(tx.description).trim());
      }

      return jsonResponse({ status: 'success', message: 'Transaction updated successfully' });
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

    return jsonResponse({ status: 'error', message: 'Unsupported POST action' });
  } catch (err) {
    return jsonResponse({ status: 'error', message: err.toString() });
  } finally {
    if (typeof calculateRemainders === 'function') {
      try {
        calculateRemainders();
      } catch (err) {
        console.warn('calculateRemainders error: ' + err);
      }
    }
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

  const startRow = Math.max(2, lastRow - (limit || 500) + 1);
  const numRows = lastRow - startRow + 1;
  const values = sheet.getRange(startRow, 1, numRows, 14).getValues();
  const tz = 'Asia/Kolkata';

  return values.map((row, idx) => {
    let dateVal = row[0];
    if (dateVal instanceof Date) {
      dateVal = Utilities.formatDate(dateVal, tz, 'dd/MM/yyyy');
    } else if (dateVal !== null && dateVal !== undefined) {
      dateVal = String(dateVal).trim();
    } else {
      dateVal = '';
    }

    return {
      id: startRow + idx,
      date: dateVal,
      credit: Number(row[1]) || 0,
      debit: Number(row[2]) || 0,
      net: Number(row[3]) || 0,
      remainder: Number(row[4]) || 0,
      subCategory: row[5],
      type: row[6],
      loans: row[7],
      account: row[8],
      category: row[9],
      year: row[10],
      month: row[11],
      debitWithoutSign: Number(row[12]) || 0
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
  return raw.slice(-(limit || 300)).map((t, idx) => ({
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

  // 3. Read only the necessary columns from Transactions to maximize speed:
  // Col B (2): Credit
  // Col C (3): Debit
  // Col H (8): Loans
  // Col I (9): Account
  var lastRow = transSheet.getLastRow();
  if (lastRow < 2) return; // No transaction rows

  var numRows = lastRow - 1; // Exclude header row

  // Read data ranges
  var creditDebitValues = transSheet.getRange(2, 2, numRows, 2).getValues(); // Cols B & C
  var loanAccountValues = transSheet.getRange(2, 8, numRows, 2).getValues(); // Cols H & I

  var runningBalances = {};
  var remainderOutput = [];

  // 4. Calculate running balances in-memory
  for (var i = 0; i < numRows; i++) {
    var credit = Number(creditDebitValues[i][0]) || 0;
    var debit = Number(creditDebitValues[i][1]) || 0;
    var loan = loanAccountValues[i][0] ? String(loanAccountValues[i][0]).trim() : "";
    var account = loanAccountValues[i][1] ? String(loanAccountValues[i][1]).trim() : "";

    // Determine the entity key: Account takes precedence; if empty, use Loans
    var entityKey = account !== "" ? account : loan;
    var currentBalance = 0;

    if (entityKey !== "") {
      if (!(entityKey in runningBalances)) {
        // Initialize opening balance
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

  // 5. Batch-write directly into Column E (starting at row 2)
  transSheet.getRange(2, 5, remainderOutput.length, 1).setValues(remainderOutput);
}