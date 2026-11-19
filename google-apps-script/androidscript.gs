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

/**
 * Executes recalculation functions if present in the Apps Script project
 * and flushes spreadsheet modifications.
 */
function runCalculationScriptsAndFlush() {
  if (typeof processTransactionsRows === 'function') {
    try {
      // processTransactionsRows() already invokes calculateRemainders()
      processTransactionsRows();
    } catch (eProcTx) {
      console.warn('processTransactionsRows error: ' + eProcTx);
      if (typeof calculateRemainders === 'function') {
        try {
          calculateRemainders();
        } catch (eCalc) {
          console.warn('calculateRemainders fallback error: ' + eCalc);
        }
      }
    }
  } else if (typeof calculateRemainders === 'function') {
    try {
      calculateRemainders();
    } catch (eCalc) {
      console.warn('calculateRemainders error: ' + eCalc);
    }
  }
  if (typeof processMutualFunds === 'function') {
    try {
      processMutualFunds();
    } catch (eProcMf) {
      console.warn('processMutualFunds error: ' + eProcMf);
    }
  }
  SpreadsheetApp.flush();
}

/**
 * Retrieves the full synchronized database across all sheets.
 */
function getFullDatabase(ss) {
  if (!ss) ss = SpreadsheetApp.getActiveSpreadsheet();
  return {
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
  };
}

function doGet(e) {
  try {
    const action = (e && e.parameter && e.parameter.action) || 'all';
    const ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === 'recalculateRemainders') {
      if (typeof calculateRemainders === 'function') {
        calculateRemainders();
      }
      SpreadsheetApp.flush();
      return jsonResponse({
        status: 'success',
        message: 'Remainders and account/loan balances recalculated successfully'
      });
    }

    if (action === 'resync') {
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'all') {
      SpreadsheetApp.flush();
      return jsonResponse({
        status: 'success',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'sips') {
      SpreadsheetApp.flush();
      return jsonResponse({
        status: 'success',
        data: getSips(ss)
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

    if (action === 'resync' || action === 'all') {
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'addTransaction') {
      const sheet = ss.getSheetByName('Transactions');
      if (!sheet) throw new Error('Transactions sheet not found');

      const tx = data.transaction || {};
      const date = tx.date || Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'dd/MM/yyyy');
      const credit = (tx.credit !== undefined && tx.credit !== null && tx.credit !== '' && Number(tx.credit) > 0) ? Number(tx.credit) : 0;
      const debitWithoutSign = (tx.debitWithoutSign !== undefined && tx.debitWithoutSign !== null && tx.debitWithoutSign !== '') ? Number(tx.debitWithoutSign) : 0;
      const subCategory = tx.subCategory || '';
      const type = tx.type || 'Expense';
      const loans = tx.loans || '';
      const account = tx.account || '';

      const lastCol = sheet.getLastColumn();
      const transHeaders = sheet.getRange(1, 1, 1, Math.max(15, lastCol)).getValues()[0];
      const cols = findTransactionsColumnIndices(transHeaders);

      let ccCtx = null;
      const getCcCtx = () => {
        if (!ccCtx) ccCtx = getCreditCardContext(ss);
        return ccCtx;
      };

      let taggedCard = String(tx.taggedCreditCard || tx.creditCard || '').trim();
      if (!taggedCard) {
        const ctx = getCcCtx();
        if (ctx && ctx.values.length >= 2) {
          const checkLoan = String(loans || '').trim().toLowerCase();
          const checkAccount = String(account || '').trim().toLowerCase();
          for (let i = 1; i < ctx.values.length; i++) {
            const cName = String(ctx.values[i][ctx.cols.colCardName - 1] || '').trim();
            if (cName && (cName.toLowerCase() === checkLoan || cName.toLowerCase() === checkAccount)) {
              taggedCard = cName;
              break;
            }
          }
          if (!taggedCard && (checkLoan === 'credit card' || checkAccount === 'credit card') && ctx.values.length === 2) {
            taggedCard = String(ctx.values[1][ctx.cols.colCardName - 1] || '').trim();
          }
        }
      }

      const txNet = (tx.net !== undefined && tx.net !== '') ? (Number(tx.net) || 0) : (credit - debitWithoutSign);
      const creditAmount = (credit > 0 ? credit : (txNet > 0 ? txNet : 0));

      let isEffectingUnbilled = true;
      if (creditAmount === 0 && (debitWithoutSign > 0 || txNet < 0)) {
        isEffectingUnbilled = true;
      } else if (tx.isEffectingUnbilled !== undefined && tx.isEffectingUnbilled !== null) {
        isEffectingUnbilled = (tx.isEffectingUnbilled === true || String(tx.isEffectingUnbilled).toLowerCase().trim() === 'true');
      } else if (tx.ccOffsetTarget !== undefined && tx.ccOffsetTarget !== null) {
        isEffectingUnbilled = (String(tx.ccOffsetTarget).toLowerCase().trim() !== 'billed');
      }

      if (cols.colTaggedCard > lastCol || !transHeaders[cols.colTaggedCard - 1]) {
        sheet.getRange(1, cols.colTaggedCard).setValue('Tagged Credit Card');
      }
      if (cols.colCcOffsetTarget > lastCol || !transHeaders[cols.colCcOffsetTarget - 1]) {
        sheet.getRange(1, cols.colCcOffsetTarget).setValue('Is Effecting Unbilled Amount');
      }

      const maxCol = Math.max(
        lastCol,
        cols.colDate, cols.colCredit, cols.colDebit, cols.colNet,
        cols.colRemainder, cols.colSubCategory, cols.colType, cols.colLoans,
        cols.colAccount, cols.colCategory, cols.colYear, cols.colMonth,
        cols.colDebitWithoutSign, cols.colProcessed, cols.colTaggedCard,
        cols.colCcOffsetTarget
      );
      const newRow = new Array(maxCol).fill('');

      if (cols.colDate > 0) newRow[cols.colDate - 1] = date;
      if (cols.colCredit > 0 && credit > 0) newRow[cols.colCredit - 1] = credit;
      if (cols.colSubCategory > 0) newRow[cols.colSubCategory - 1] = subCategory;
      if (cols.colType > 0) newRow[cols.colType - 1] = type;
      if (cols.colLoans > 0) newRow[cols.colLoans - 1] = loans;
      if (cols.colAccount > 0) newRow[cols.colAccount - 1] = account;
      if (cols.colDebitWithoutSign > 0) newRow[cols.colDebitWithoutSign - 1] = debitWithoutSign;
      if (cols.colTaggedCard > 0 && taggedCard) newRow[cols.colTaggedCard - 1] = taggedCard;
      if (cols.colCcOffsetTarget > 0) {
        if (taggedCard || String(loans).toUpperCase() === 'CREDIT CARD' || tx.isEffectingUnbilled !== undefined) {
          newRow[cols.colCcOffsetTarget - 1] = isEffectingUnbilled;
        } else {
          newRow[cols.colCcOffsetTarget - 1] = '';
        }
      }

      sheet.appendRow(newRow);

      // If tagged to a credit card, update its Unbilled Amount or Billed Amount in 'Credit Cards' sheet
      if (taggedCard) {
        try {
          const ctx = getCcCtx();
          if (ctx && ctx.values.length >= 2) {
            const { sheet: ccSheet, cols: ccCols, values: ccValues } = ctx;
            const targetName = String(taggedCard).toLowerCase().trim();
            for (let i = 1; i < ccValues.length; i++) {
              const rowCardName = String(ccValues[i][ccCols.colCardName - 1] || '').toLowerCase().trim();
              if (rowCardName === targetName) {
                const currUnbilled = Number(ccValues[i][ccCols.colUnbilled - 1]) || 0;
                const currBilled = Number(ccValues[i][ccCols.colBilled - 1]) || 0;

                if (!isEffectingUnbilled && creditAmount > 0) {
                  // Offset Billed Amount with credit/payment
                  const newBilled = currBilled - creditAmount;
                  if (currBilled > 0 && (newBilled <= 0 || Math.abs(creditAmount - currBilled) < 0.01)) {
                    applyMarkCreditCardPaid(ccSheet, ccCols, i + 1, ccValues);
                  } else {
                    ccSheet.getRange(i + 1, ccCols.colBilled).setValue(Math.round(newBilled * 100) / 100);
                    if (newBilled > 0 && ccCols.colStatus > 0) {
                      ccSheet.getRange(i + 1, ccCols.colStatus).setValue('Unpaid');
                    }
                  }
                } else {
                  // Offset Unbilled Amount (for all debit/expense transactions, or credit transactions targeting unbilled)
                  let delta = 0;
                  if (debitWithoutSign > 0) delta += debitWithoutSign;
                  if (credit > 0) delta -= credit;
                  if (delta === 0 && tx.net !== undefined) {
                    delta = -Number(tx.net) || 0;
                  }
                  let newUnbilled = currUnbilled + delta;
                  if (currUnbilled >= 0 && newUnbilled < 0) newUnbilled = 0;
                  ccSheet.getRange(i + 1, ccCols.colUnbilled).setValue(Math.round(newUnbilled * 100) / 100);
                }
                break;
              }
            }
          }
        } catch (ccErr) {
          console.warn('Error updating credit card amount: ' + ccErr);
        }
      }

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Transaction added successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'deleteTransaction') {
      const sheet = ss.getSheetByName('Transactions');
      if (!sheet) throw new Error('Transactions sheet not found');

      const tx = data.transaction || {};
      let rowId = Number(data.id || tx.id);
      const lastRow = sheet.getLastRow();
      const lastCol = sheet.getLastColumn();

      let transHeaders;
      let cols;
      let rowValues = null;

      // Fallback row matching if rowId is invalid or was temporary
      if (!rowId || rowId < 2 || rowId > lastRow) {
        const vals = sheet.getDataRange().getValues();
        transHeaders = vals[0] || [];
        cols = findTransactionsColumnIndices(transHeaders);
        for (let i = vals.length - 1; i >= 1; i--) {
          const rDate = cols.colDate > 0 ? (vals[i][cols.colDate - 1] instanceof Date ? Utilities.formatDate(vals[i][cols.colDate - 1], 'Asia/Kolkata', 'dd/MM/yyyy') : String(vals[i][cols.colDate - 1] || '').trim()) : '';
          const rSub = cols.colSubCategory > 0 ? String(vals[i][cols.colSubCategory - 1] || '').trim() : '';
          if (tx.date && rDate === String(tx.date).trim() && (!tx.subCategory || rSub === String(tx.subCategory).trim())) {
            rowId = i + 1;
            rowValues = vals[i];
            break;
          }
        }
      } else {
        transHeaders = sheet.getRange(1, 1, 1, Math.max(15, lastCol)).getValues()[0];
        cols = findTransactionsColumnIndices(transHeaders);
      }

      if (!rowId || rowId < 2 || rowId > lastRow) {
        throw new Error('Invalid transaction row ID: ' + (data.id || tx.id));
      }

      // Read row values before deleting to reverse credit card effect if tagged
      if (!rowValues) {
        const readCols = Math.max(lastCol, cols.colTaggedCard, cols.colDebitWithoutSign, cols.colCredit, cols.colDebit, cols.colLoans, cols.colCcOffsetTarget);
        rowValues = sheet.getRange(rowId, 1, 1, readCols).getValues()[0];
      }

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

      let ccCtx = null;
      const getCcCtx = () => {
        if (!ccCtx) ccCtx = getCreditCardContext(ss);
        return ccCtx;
      };

      if (!rowTaggedCard && rowLoan.toUpperCase() === 'CREDIT CARD') {
        const ctx = getCcCtx();
        if (ctx && ctx.values.length === 2) {
          rowTaggedCard = String(ctx.values[1][ctx.cols.colCardName - 1] || '').trim();
        }
      }

      let cr = rowCredit;
      if (cr === 0 && tx.credit) cr = Number(tx.credit) || 0;
      if (cr === 0 && tx.net && Number(tx.net) > 0) cr = Number(tx.net);

      const rawOffsetVal = (cols.colCcOffsetTarget > 0 && rowValues.length >= cols.colCcOffsetTarget) ? rowValues[cols.colCcOffsetTarget - 1] : '';
      let isEffectingUnbilled = true;
      if (cr === 0 && (rowDebitWithoutSign > 0 || rowDebit < 0 || Number(tx.debitWithoutSign) > 0 || Number(tx.debit) < 0)) {
        isEffectingUnbilled = true;
      } else if (rawOffsetVal !== '' && rawOffsetVal !== null && rawOffsetVal !== undefined) {
        if (typeof rawOffsetVal === 'boolean') {
          isEffectingUnbilled = rawOffsetVal;
        } else {
          const s = String(rawOffsetVal).toLowerCase().trim();
          isEffectingUnbilled = !(s === 'false' || s === 'billed' || s === 'no' || s === '0');
        }
      } else if (tx.isEffectingUnbilled !== undefined && tx.isEffectingUnbilled !== null) {
        isEffectingUnbilled = (tx.isEffectingUnbilled === true || String(tx.isEffectingUnbilled).toLowerCase().trim() === 'true');
      } else if (tx.ccOffsetTarget) {
        isEffectingUnbilled = String(tx.ccOffsetTarget).toLowerCase().trim() !== 'billed';
      }

      // Reverse effect on target Credit Card
      if (rowTaggedCard) {
        try {
          const ctx = getCcCtx();
          if (ctx && ctx.values.length >= 2) {
            const { sheet: ccSheet, cols: ccCols, values: ccValues } = ctx;
            const targetName = rowTaggedCard.toLowerCase().trim();
            for (let i = 1; i < ccValues.length; i++) {
              const rowCardName = String(ccValues[i][ccCols.colCardName - 1] || '').toLowerCase().trim();
              if (rowCardName === targetName) {
                if (!isEffectingUnbilled && cr > 0) {
                  // Reverse Billed Amount
                  const currBilled = Number(ccValues[i][ccCols.colBilled - 1]) || 0;
                  const currStatus = String(ccValues[i][ccCols.colStatus - 1] || '').trim();
                  const restoredBilled = Math.round((currBilled + cr) * 100) / 100;
                  ccSheet.getRange(i + 1, ccCols.colBilled).setValue(restoredBilled);
                  if (currBilled === 0 || currStatus.toLowerCase() === 'paid') {
                    // Roll back due date by 1 month and mark Unpaid
                    let currDueDate = ccValues[i][ccCols.colDueDate - 1];
                    if (currDueDate instanceof Date) {
                      currDueDate = Utilities.formatDate(currDueDate, 'Asia/Kolkata', 'dd/MM/yyyy');
                    } else {
                      currDueDate = String(currDueDate || '').trim();
                    }
                    const rolledBackDate = rollbackDateByOneMonth(currDueDate);
                    if (rolledBackDate && ccCols.colDueDate > 0) {
                      ccSheet.getRange(i + 1, ccCols.colDueDate).setValue(rolledBackDate);
                    }
                    if (ccCols.colStatus > 0) {
                      ccSheet.getRange(i + 1, ccCols.colStatus).setValue('Unpaid');
                    }
                  }
                } else {
                  // Reverse Unbilled Amount
                  const currUnbilled = Number(ccValues[i][ccCols.colUnbilled - 1]) || 0;
                  let dws = rowDebitWithoutSign;
                  if (dws === 0 && tx.debitWithoutSign) dws = Number(tx.debitWithoutSign) || 0;
                  let delta = 0;
                  if (dws > 0) delta += dws;
                  if (cr > 0) delta -= cr;
                  if (delta === 0 && (rowDebit !== 0 || tx.debit || tx.net)) {
                    delta = -Number(rowDebit || tx.debit || tx.net || 0);
                  }
                  let newUnbilled = currUnbilled - delta;
                  if (newUnbilled < 0) newUnbilled = 0;
                  newUnbilled = Math.round(newUnbilled * 100) / 100;
                  ccSheet.getRange(i + 1, ccCols.colUnbilled).setValue(newUnbilled);
                }
                break;
              }
            }
          }
        } catch (ccErr) {
          console.warn('Error reversing credit card amount on delete: ' + ccErr);
        }
      }

      sheet.deleteRow(rowId);
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Transaction deleted successfully',
        data: getFullDatabase(ss)
      });
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
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'SIP status updated to ' + status,
        data: getFullDatabase(ss)
      });
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
      const hasLastPaid = p.lastPaid !== undefined && p.lastPaid !== null;
      const hasDueDate = p.dueDate !== undefined && p.dueDate !== null;
      if (hasLastPaid && hasDueDate) {
        sheet.getRange(matchedRow, 4, 1, 2).setValues([[String(p.lastPaid).trim(), String(p.dueDate).trim()]]);
      } else {
        if (hasLastPaid) {
          sheet.getRange(matchedRow, 4).setValue(String(p.lastPaid).trim());
        }
        if (hasDueDate) {
          sheet.getRange(matchedRow, 5).setValue(String(p.dueDate).trim());
        }
      }
      if (p.installmentsRemaining !== undefined && p.installmentsRemaining !== null && String(p.installmentsRemaining).trim() === '0') {
        sheet.getRange(matchedRow, 7).setValue('0');
      }

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Policy dates updated successfully',
        data: getFullDatabase(ss)
      });
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
        '',              // Col 9: Category
        id,              // Col 10: Id
        password,        // Col 11: Password
        sumAssured,      // Col 12: Sum Assured
        notes            // Col 13: Notes / Remarks
      ]);

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Policy added successfully',
        data: getFullDatabase(ss)
      });
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

      const rawRow = values[matchedRow - 1] || [];
      const row = new Array(9);
      for (let c = 0; c < 9; c++) {
        row[c] = (rawRow[c + 1] !== undefined && rawRow[c + 1] !== null) ? rawRow[c + 1] : '';
      }
      if (sip.role !== undefined && sip.role !== null) row[0] = String(sip.role).trim();
      if (sip.currentSip !== undefined && sip.currentSip !== null) row[1] = Number(sip.currentSip) || 0;
      if (sip.currentMonth !== undefined && sip.currentMonth !== null) row[2] = String(sip.currentMonth).trim();
      if (sip.currentStatus !== undefined && sip.currentStatus !== null) row[3] = String(sip.currentStatus).trim();
      if (sip.nextSip !== undefined && sip.nextSip !== null) row[4] = Number(sip.nextSip) || 0;
      if (sip.nextMonth !== undefined && sip.nextMonth !== null) row[5] = String(sip.nextMonth).trim();
      if (sip.nextStatus !== undefined && sip.nextStatus !== null) row[6] = String(sip.nextStatus).trim();
      if (sip.bankAccount !== undefined && sip.bankAccount !== null) row[7] = String(sip.bankAccount).trim();
      if (sip.platform !== undefined && sip.platform !== null) row[8] = String(sip.platform).trim();

      sheet.getRange(matchedRow, 2, 1, 9).setValues([row]);
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'SIP updated successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'addSip') {
      const sheet = ss.getSheetByName('SIP Tracer');
      if (!sheet) throw new Error('SIP Tracer sheet not found');

      const sip = data.sip || {};
      const fundName = String(sip.fundName || '').trim();
      if (!fundName) throw new Error('Fund Name is required');

      const role = String(sip.role || '').trim();
      const currentSip = Number(sip.currentSip) || 0;
      const currentMonth = String(sip.currentMonth || '').trim();
      const currentStatus = String(sip.currentStatus || 'Not Paid').trim();
      const nextSip = Number(sip.nextSip) || currentSip;
      const nextMonth = String(sip.nextMonth || '').trim();
      const nextStatus = String(sip.nextStatus || 'Not Paid').trim();
      const bankAccount = String(sip.bankAccount || '').trim();
      const platform = String(sip.platform || '').trim();

      sheet.appendRow([
        fundName,       // 1: Fund Name
        role,           // 2: Role in Architecture
        currentSip,     // 3: This Month's SIP
        currentMonth,   // 4: This Month
        currentStatus,  // 5: Payment Status
        nextSip,        // 6: Next SIP (Final)
        nextMonth,      // 7: Next Month
        nextStatus,     // 8: Next Month's Payment Status
        bankAccount,    // 9: Linked Bank Account
        platform        // 10: Platform
      ]);

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'SIP added successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'deleteSip') {
      const sheet = ss.getSheetByName('SIP Tracer');
      if (!sheet) throw new Error('SIP Tracer sheet not found');

      const fundName = String(data.fundName || (data.sip && data.sip.fundName) || '').toLowerCase().trim();
      if (!fundName) throw new Error('Fund Name is required to delete SIP');

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

      sheet.deleteRow(matchedRow);
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'SIP deleted successfully',
        data: getFullDatabase(ss)
      });
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

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Mutual Fund transaction recorded successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'addCreditCard') {
      const ctx = getCreditCardContext(ss);
      if (!ctx) throw new Error('Credit Cards sheet not found');
      const { sheet, cols, values } = ctx;

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is mandatory');

      const targetLower = cardName.toLowerCase();
      for (let i = 1; i < values.length; i++) {
        if (String(values[i][cols.colCardName - 1] || '').toLowerCase().trim() === targetLower) {
          throw new Error('A credit card named "' + cardName + '" already exists');
        }
      }

      const billedAmount = Number(c.billedAmount) || 0;
      const unbilledAmount = 0; // Unbilled amount not taken during creation, defaults to 0
      const dueDate = String(c.dueDate || '').trim();
      const lastPaid = '';
      const status = billedAmount > 0 ? 'Unpaid' : 'Paid';

      const rowLen = Math.max(values[0].length, cols.colCardName, cols.colBilled, cols.colUnbilled, cols.colDueDate, cols.colLastPaid, cols.colStatus);
      const newRow = new Array(rowLen).fill('');
      newRow[cols.colCardName - 1] = cardName;
      newRow[cols.colBilled - 1] = billedAmount;
      newRow[cols.colUnbilled - 1] = unbilledAmount;
      newRow[cols.colDueDate - 1] = dueDate;
      if (cols.colLastPaid > 0) newRow[cols.colLastPaid - 1] = lastPaid;
      if (cols.colStatus > 0) newRow[cols.colStatus - 1] = status;

      sheet.appendRow(newRow);
      runCalculationScriptsAndFlush();

      return jsonResponse({
        status: 'success',
        message: 'Credit card ' + cardName + ' added successfully',
        data: getFullDatabase(ss),
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
      const ctx = getCreditCardContext(ss);
      if (!ctx) throw new Error('Credit Cards sheet not found');
      const { sheet, cols, values } = ctx;

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is required');

      const matchedRow = findCreditCardRowIndex(values, cols.colCardName, cardName);
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

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Credit card details updated successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'markCreditCardPaid') {
      const ctx = getCreditCardContext(ss);
      if (!ctx) throw new Error('Credit Cards sheet not found');
      const { sheet, cols, values } = ctx;

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is required');

      const matchedRow = findCreditCardRowIndex(values, cols.colCardName, cardName);
      if (matchedRow === -1) {
        throw new Error('Credit Card not found: ' + cardName);
      }

      const nextDueDate = applyMarkCreditCardPaid(sheet, cols, matchedRow, values);

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: cardName + ' marked as paid! Next due date moved 1 month to ' + nextDueDate,
        nextDueDate: nextDueDate,
        data: getFullDatabase(ss)
      });
    }

    if (action === 'markCreditCardBillGenerated') {
      const ctx = getCreditCardContext(ss);
      if (!ctx) throw new Error('Credit Cards sheet not found');
      const { sheet, cols, values } = ctx;

      const c = data.card || {};
      const cardName = String(c.cardName || '').trim();
      if (!cardName) throw new Error('Card name is required');

      const matchedRow = findCreditCardRowIndex(values, cols.colCardName, cardName);
      if (matchedRow === -1) {
        throw new Error('Credit Card not found: ' + cardName);
      }

      let unbilledVal = Number(values[matchedRow - 1][cols.colUnbilled - 1]) || 0;
      if (c.unbilledAmount !== undefined && Number(c.unbilledAmount) > 0 && unbilledVal === 0) {
        unbilledVal = Number(c.unbilledAmount);
      }

      sheet.getRange(matchedRow, cols.colBilled).setValue(unbilledVal);
      sheet.getRange(matchedRow, cols.colUnbilled).setValue(0);

      if (cols.colStatus > 0) {
        sheet.getRange(matchedRow, cols.colStatus).setValue(unbilledVal > 0 ? 'Unpaid' : 'Paid');
      }

      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: cardName + ' bill generated: ₹' + unbilledVal + ' billed, unbilled reset to ₹0',
        billedAmount: unbilledVal,
        unbilledAmount: 0,
        data: getFullDatabase(ss)
      });
    }

    if (action === 'addGoal') {
      const sheet = ss.getSheetByName('Goals');
      if (!sheet) throw new Error('Goals sheet not found');

      const g = data.goal || {};
      const goalName = String(g.name || g.goalName || '').trim();
      if (!goalName) throw new Error('Goal Name is required');

      const goalAmount = Number(g.targetAmount || g.goalAmount) || 0;
      const currentAmount = Number(g.currentAmount || g.occuredTillDate) || 0;
      const contributionJson = typeof g.contributionJson === 'object' ? JSON.stringify(g.contributionJson, null, 2) : String(g.contributionJson || '{}').trim();

      const rows = sheet.getDataRange().getValues();
      validateGoalAllocations(rows, contributionJson, -1);

      sheet.appendRow([
        goalName,
        goalAmount,
        currentAmount,
        '',
        contributionJson
      ]);
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Goal "' + goalName + '" added successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'updateGoal' || action === 'editGoal') {
      const sheet = ss.getSheetByName('Goals');
      if (!sheet) throw new Error('Goals sheet not found');

      const oldName = String(data.oldName || data.oldGoalName || (data.goal && data.goal.oldName) || (data.goal && data.goal.name) || '').trim();
      const g = data.goal || {};
      const newName = String(g.name || g.goalName || oldName).trim();
      if (!oldName && !newName) throw new Error('Goal Name is required');

      const goalAmount = Number(g.targetAmount || g.goalAmount) || 0;
      const currentAmount = Number(g.currentAmount || g.occuredTillDate) || 0;
      const contributionJson = typeof g.contributionJson === 'object' ? JSON.stringify(g.contributionJson, null, 2) : String(g.contributionJson || '{}').trim();

      const values = sheet.getDataRange().getValues();
      const oldLower = oldName.toLowerCase();
      const newLower = newName.toLowerCase();
      let matchedRow = -1;
      for (let i = 1; i < values.length; i++) {
        const rowName = String(values[i][0] || '').trim().toLowerCase();
        if (rowName === oldLower || rowName === newLower) {
          matchedRow = i + 1;
          break;
        }
      }

      if (matchedRow === -1) {
        throw new Error('Goal not found: ' + (oldName || newName));
      }

      validateGoalAllocations(values, contributionJson, matchedRow);

      // Batch-update columns A-C without overwriting Column D formula, then update Column E
      sheet.getRange(matchedRow, 1, 1, 3).setValues([[newName, goalAmount, currentAmount]]);
      sheet.getRange(matchedRow, 5).setValue(contributionJson);
      runCalculationScriptsAndFlush();

      return jsonResponse({
        status: 'success',
        message: 'Goal "' + newName + '" updated successfully',
        data: getFullDatabase(ss)
      });
    }

    if (action === 'deleteGoal') {
      const sheet = ss.getSheetByName('Goals');
      if (!sheet) throw new Error('Goals sheet not found');

      const goalName = String(data.name || (data.goal && data.goal.name) || '').trim();
      if (!goalName) throw new Error('Goal Name is required to delete');

      const vals = sheet.getDataRange().getValues();
      const targetLower = goalName.toLowerCase();
      let rowToDelete = -1;
      for (let i = 1; i < vals.length; i++) {
        if (String(vals[i][0] || '').trim().toLowerCase() === targetLower) {
          rowToDelete = i + 1;
          break;
        }
      }

      if (rowToDelete === -1) {
        throw new Error('Goal not found: ' + goalName);
      }

      sheet.deleteRow(rowToDelete);
      runCalculationScriptsAndFlush();
      return jsonResponse({
        status: 'success',
        message: 'Goal "' + goalName + '" deleted successfully',
        data: getFullDatabase(ss)
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
function getCreditCardContext(ss) {
  const sheet = ss.getSheetByName('Credit Cards');
  if (!sheet) return null;
  const values = sheet.getDataRange().getValues();
  if (values.length === 0) return null;
  const cols = findCreditCardColumnIndices(values[0]);
  return { sheet, cols, values };
}

function findCreditCardRowIndex(values, colCardName, cardName) {
  const targetLower = String(cardName || '').toLowerCase().trim();
  for (let i = 1; i < values.length; i++) {
    if (String(values[i][colCardName - 1] || '').toLowerCase().trim() === targetLower) {
      return i + 1;
    }
  }
  return -1;
}

function validateGoalAllocations(rows, contributionJson, excludeRowIndex) {
  try {
    const incomingContrib = JSON.parse(contributionJson);
    const accAllocations = {};
    const mfAllocations = {};

    for (let i = 1; i < rows.length; i++) {
      if (i + 1 === excludeRowIndex) continue;
      const rowJsonStr = String(rows[i][4] || '').trim();
      if (!rowJsonStr || rowJsonStr === '{}') continue;
      try {
        const exObj = JSON.parse(rowJsonStr);
        if (exObj.accounts && typeof exObj.accounts === 'object') {
          for (const acc in exObj.accounts) {
            const cleanAcc = acc.trim().toUpperCase();
            let p = Number(exObj.accounts[acc]) || 0;
            if (p > 1.0) p = p / 100.0;
            accAllocations[cleanAcc] = (accAllocations[cleanAcc] || 0) + p;
          }
        }
        if (Array.isArray(exObj.mutual_funds)) {
          for (const mf of exObj.mutual_funds) {
            const mfVal = String(mf.value || '').trim().toLowerCase();
            let p = Number(mf.percentage) || 1.0;
            if (p > 1.0) p = p / 100.0;
            mfAllocations[mfVal] = (mfAllocations[mfVal] || 0) + p;
          }
        }
      } catch (e) {}
    }

    if (incomingContrib.accounts && typeof incomingContrib.accounts === 'object') {
      for (const acc in incomingContrib.accounts) {
        const cleanAcc = acc.trim().toUpperCase();
        let p = Number(incomingContrib.accounts[acc]) || 0;
        if (p > 1.0) p = p / 100.0;
        const total = (accAllocations[cleanAcc] || 0) + p;
        if (total > 1.0001) {
          throw new Error('Total allocation for account "' + acc + '" exceeds 100% (' + (total * 100).toFixed(1) + '% across goals). Allocation must be <= 100%.');
        }
      }
    }

    if (Array.isArray(incomingContrib.mutual_funds)) {
      for (const mf of incomingContrib.mutual_funds) {
        const mfVal = String(mf.value || '').trim().toLowerCase();
        let p = Number(mf.percentage) || 1.0;
        if (p > 1.0) p = p / 100.0;
        const total = (mfAllocations[mfVal] || 0) + p;
        if (total > 1.0001) {
          throw new Error('Total allocation for mutual fund "' + (mf.value || '') + '" exceeds 100% (' + (total * 100).toFixed(1) + '% across goals). Allocation must be <= 100%.');
        }
      }
    }
  } catch (err) {
    if (err.message && err.message.indexOf('Total allocation') !== -1) {
      throw err;
    }
  }
}

function getSheetValues(ss, sheetName) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) return [];
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return [];

  const tz = 'Asia/Kolkata';
  const headers = values[0];
  const validHeaders = [];
  for (let c = 0; c < headers.length; c++) {
    if (headers[c]) {
      validHeaders.push({ name: headers[c].toString().trim(), index: c });
    }
  }

  const results = [];
  for (let i = 1; i < values.length; i++) {
    const row = values[i];
    let isEmpty = true;
    for (let c = 0; c < row.length; c++) {
      if (row[c] !== '' && row[c] !== null) {
        isEmpty = false;
        break;
      }
    }
    if (isEmpty) continue;

    const obj = {};
    for (let k = 0; k < validHeaders.length; k++) {
      const h = validHeaders[k];
      let val = row[h.index];
      if (val instanceof Date) {
        val = Utilities.formatDate(val, tz, 'dd/MM/yyyy');
      }
      obj[h.name] = val;
    }
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
    cols.colDebitWithoutSign, cols.colProcessed, cols.colTaggedCard,
    cols.colCcOffsetTarget
  );
  const values = sheet.getRange(startRow, 1, numRows, maxCol).getValues();
  const tz = 'Asia/Kolkata';

  // Read credit cards list once for single-card fallback
  const creditCardNamesList = [];
  try {
    const ccCtx = getCreditCardContext(ss);
    if (ccCtx && ccCtx.values.length >= 2) {
      for (let cIdx = 1; cIdx < ccCtx.values.length; cIdx++) {
        const cName = String(ccCtx.values[cIdx][ccCtx.cols.colCardName - 1] || '').trim();
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

    const credit = cols.colCredit > 0 ? (Number(row[cols.colCredit - 1]) || 0) : 0;
    let debit = cols.colDebit > 0 ? (Number(row[cols.colDebit - 1]) || 0) : 0;
    let net = cols.colNet > 0 ? (Number(row[cols.colNet - 1]) || 0) : 0;
    const remainder = cols.colRemainder > 0 ? (Number(row[cols.colRemainder - 1]) || 0) : 0;
    let dws = cols.colDebitWithoutSign > 0 ? (Number(row[cols.colDebitWithoutSign - 1]) || 0) : 0;

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
    if ((year === 0 || month === 0) && dateVal && dateVal.includes('/')) {
      const dParts = dateVal.split('/');
      if (dParts.length === 3) {
        if (year === 0) year = Number(dParts[2]) || new Date().getFullYear();
        if (month === 0) month = Number(dParts[1]) || (new Date().getMonth() + 1);
      }
    }

    const loanVal = cols.colLoans > 0 ? String(row[cols.colLoans - 1] || '').trim() : '';
    const subCatVal = cols.colSubCategory > 0 ? String(row[cols.colSubCategory - 1] || '').trim() : '';
    const typeVal = cols.colType > 0 ? String(row[cols.colType - 1] || '').trim() : 'Expense';
    const accVal = cols.colAccount > 0 ? String(row[cols.colAccount - 1] || '').trim() : '';
    const catVal = cols.colCategory > 0 ? String(row[cols.colCategory - 1] || '').trim() : '';
    const procVal = cols.colProcessed > 0 ? String(row[cols.colProcessed - 1] || '').trim() : '';

    let taggedCard = (cols.colTaggedCard > 0 && row.length >= cols.colTaggedCard && row[cols.colTaggedCard - 1]) ? String(row[cols.colTaggedCard - 1]).trim() : '';
    if (!taggedCard && loanVal.toUpperCase() === 'CREDIT CARD' && creditCardNamesList.length === 1) {
      taggedCard = creditCardNamesList[0];
    }

    let isEffectingUnbilled = true;
    if (credit === 0 && (dws > 0 || debit < 0)) {
      isEffectingUnbilled = true;
    } else if (cols.colCcOffsetTarget > 0 && row.length >= cols.colCcOffsetTarget) {
      const val = row[cols.colCcOffsetTarget - 1];
      if (typeof val === 'boolean') {
        isEffectingUnbilled = val;
      } else {
        const s = String(val || '').toLowerCase().trim();
        if (s === 'false' || s === 'billed' || s === 'no' || s === '0') {
          isEffectingUnbilled = false;
        }
      }
    }

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
      taggedCreditCard: taggedCard,
      isEffectingUnbilled: isEffectingUnbilled,
      ccOffsetTarget: isEffectingUnbilled ? 'unbilled' : 'billed',
      isProcessed: procVal === 'Done' || procVal !== ''
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
    const rawAchieved = (g['Achived Till Date'] !== undefined && g['Achived Till Date'] !== '')
      ? Number(g['Achived Till Date'])
      : ((g['Achieved Till Date'] !== undefined && g['Achieved Till Date'] !== '')
        ? Number(g['Achieved Till Date'])
        : (target > 0 ? (current / target) : 0));
    const achievedPct = target > 0 ? Math.round(rawAchieved * 1000) / 10 : 0;
    return {
      name: g['Goals Name'],
      targetAmount: target,
      currentAmount: current,
      achievedPercent: achievedPct,
      savingAllocationPercent: (Number(g['Percentage Of saving To Put']) || 0) * 100,
      contributionJson: String(g['Contribution JSON'] || '{}').trim()
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
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const transSheet = ss.getSheetByName('Transactions');
  if (!transSheet) {
    Logger.log("Error: 'Transactions' sheet not found.");
    return;
  }

  const accBalances = {};
  const accSheet = ss.getSheetByName('Account Opening Balance');
  if (accSheet) {
    const accData = accSheet.getDataRange().getValues();
    for (let i = 1; i < accData.length; i++) {
      const accName = accData[i][0] ? String(accData[i][0]).trim() : '';
      if (accName !== '') {
        accBalances[accName] = Number(accData[i][1]) || 0;
      }
    }
  }

  const loanBalances = {};
  const loanSheet = ss.getSheetByName('Loans Opening Balance');
  if (loanSheet) {
    const loanData = loanSheet.getDataRange().getValues();
    for (let i = 1; i < loanData.length; i++) {
      const loanName = loanData[i][0] ? String(loanData[i][0]).trim() : '';
      if (loanName !== '') {
        loanBalances[loanName] = Number(loanData[i][1]) || 0;
      }
    }
  }

  const lastRow = transSheet.getLastRow();
  if (lastRow < 2) return;

  const numRows = lastRow - 1;
  const lastCol = transSheet.getLastColumn();
  const transHeaders = transSheet.getRange(1, 1, 1, Math.max(15, lastCol)).getValues()[0];
  const cols = findTransactionsColumnIndices(transHeaders);
  const maxReadCol = Math.max(
    lastCol,
    cols.colCredit,
    cols.colDebit,
    cols.colLoans,
    cols.colAccount,
    cols.colDebitWithoutSign,
    cols.colRemainder
  );

  const transData = transSheet.getRange(2, 1, numRows, maxReadCol).getValues();
  const runningBalances = {};
  const remainderOutput = new Array(numRows);

  for (let i = 0; i < numRows; i++) {
    const row = transData[i];
    const credit = cols.colCredit > 0 ? (Number(row[cols.colCredit - 1]) || 0) : 0;
    let debit = cols.colDebit > 0 ? (Number(row[cols.colDebit - 1]) || 0) : 0;
    const loan = cols.colLoans > 0 && row[cols.colLoans - 1] ? String(row[cols.colLoans - 1]).trim() : '';
    const account = cols.colAccount > 0 && row[cols.colAccount - 1] ? String(row[cols.colAccount - 1]).trim() : '';
    const dws = cols.colDebitWithoutSign > 0 ? (Number(row[cols.colDebitWithoutSign - 1]) || 0) : 0;

    if (debit === 0 && dws !== 0) {
      debit = -Math.abs(dws);
    }

    const entityKey = account !== '' ? account : loan;
    let currentBalance = 0;

    if (entityKey !== '') {
      if (!(entityKey in runningBalances)) {
        if (account !== '') {
          runningBalances[entityKey] = (account in accBalances) ? accBalances[account] : 0;
        } else {
          runningBalances[entityKey] = (loan in loanBalances) ? loanBalances[loan] : 0;
        }
      }

      runningBalances[entityKey] += (credit + debit);
      currentBalance = Math.round(runningBalances[entityKey] * 100) / 100;
    }

    remainderOutput[i] = [currentBalance];
  }

  if (cols.colRemainder > 0) {
    transSheet.getRange(2, cols.colRemainder, remainderOutput.length, 1).setValues(remainderOutput);
  }
  SpreadsheetApp.flush();
}

function applyMarkCreditCardPaid(sheet, cols, rowIndex, values) {
  const todayStr = Utilities.formatDate(new Date(), 'Asia/Kolkata', 'dd/MM/yyyy');
  if (cols.colBilled > 0) {
    sheet.getRange(rowIndex, cols.colBilled).setValue(0);
  }

  let currDueDate = '';
  if (values && values.length >= rowIndex && cols.colDueDate > 0) {
    currDueDate = values[rowIndex - 1][cols.colDueDate - 1];
  } else if (cols.colDueDate > 0) {
    currDueDate = sheet.getRange(rowIndex, cols.colDueDate).getValue();
  }

  if (currDueDate instanceof Date) {
    currDueDate = Utilities.formatDate(currDueDate, 'Asia/Kolkata', 'dd/MM/yyyy');
  } else {
    currDueDate = String(currDueDate || '').trim();
  }
  if (!currDueDate) {
    currDueDate = todayStr;
  }
  const nextDueDate = advanceDateByOneMonth(currDueDate);
  if (nextDueDate && cols.colDueDate > 0) {
    sheet.getRange(rowIndex, cols.colDueDate).setValue(nextDueDate);
  }

  if (cols.colLastPaid > 0) {
    sheet.getRange(rowIndex, cols.colLastPaid).setValue(todayStr);
  }
  if (cols.colStatus > 0) {
    sheet.getRange(rowIndex, cols.colStatus).setValue('Paid');
  }
  return nextDueDate;
}

function advanceDateByOneMonth(dateStr) {
  if (!dateStr || typeof dateStr !== 'string') return dateStr;
  const parts = dateStr.trim().split('/');
  if (parts.length === 3) {
    let day = parseInt(parts[0], 10);
    let month = parseInt(parts[1], 10);
    let year = parseInt(parts[2], 10);

    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }

    const maxDaysInMonth = new Date(year, month, 0).getDate();
    if (day > maxDaysInMonth) {
      day = maxDaysInMonth;
    }

    const dd = day < 10 ? '0' + day : '' + day;
    const mm = month < 10 ? '0' + month : '' + month;
    return dd + '/' + mm + '/' + year;
  }
  return dateStr;
}

function rollbackDateByOneMonth(dateStr) {
  if (!dateStr || typeof dateStr !== 'string') return dateStr;
  const parts = dateStr.trim().split('/');
  if (parts.length === 3) {
    let day = parseInt(parts[0], 10);
    let month = parseInt(parts[1], 10);
    let year = parseInt(parts[2], 10);

    month -= 1;
    if (month < 1) {
      month = 12;
      year -= 1;
    }

    const maxDaysInMonth = new Date(year, month, 0).getDate();
    if (day > maxDaysInMonth) {
      day = maxDaysInMonth;
    }

    const dd = day < 10 ? '0' + day : '' + day;
    const mm = month < 10 ? '0' + month : '' + month;
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
  let colCcOffsetTarget = -1;

  if (headers && headers.length) {
    for (let c = 0; c < headers.length; c++) {
      const h = String(headers[c] || '').toLowerCase().trim();
      if (!h) continue;
      const idx = c + 1;

      if (colTaggedCard === -1 && (
        h.includes('tagged') ||
        h === 'credit card' ||
        h === 'credit_card' ||
        h === 'card' ||
        (h.includes('credit') && h.includes('card'))
      )) {
        colTaggedCard = idx;
      } else if (colDebitWithoutSign === -1 && (
        (h.includes('debit') && (h.includes('without') || h.includes('sign') || h.includes('w/o') || h.includes('abs') || h.includes('no') || h.includes('pos'))) ||
        h === 'dws'
      )) {
        colDebitWithoutSign = idx;
      } else if (colSubCategory === -1 && (
        h.includes('sub') ||
        h.includes('subcategory') ||
        h.includes('sub category') ||
        h.includes('sub-cat')
      )) {
        colSubCategory = idx;
      } else if (colRemainder === -1 && (
        h.includes('remainder') ||
        h.includes('running') ||
        (h.includes('balance') && !h.includes('account') && !h.includes('loan')) ||
        h.includes('cumulative')
      )) {
        colRemainder = idx;
      } else if (colDebit === -1 && (
        (h.includes('debit') && !h.includes('without') && !h.includes('sign') && !h.includes('w/o')) ||
        h === 'dr' ||
        h.includes('dr ') ||
        h.includes('dr amount') ||
        h === 'outflow'
      )) {
        colDebit = idx;
      } else if (colCredit === -1 && (
        (h.includes('credit') && !h.includes('card')) ||
        h === 'cr' ||
        h.includes('cr ') ||
        h.includes('cr amount') ||
        h === 'inflow' ||
        h === 'deposit'
      )) {
        colCredit = idx;
      } else if (colNet === -1 && (
        h.includes('net') ||
        (h.includes('amount') && !h.includes('credit') && !h.includes('debit') && !h.includes('bill') && !h.includes('cr') && !h.includes('dr'))
      )) {
        colNet = idx;
      } else if (colLoans === -1 && (h.includes('loan') || h.includes('liability'))) {
        colLoans = idx;
      } else if (colType === -1 && h.includes('type')) {
        colType = idx;
      } else if (colAccount === -1 && (
        h.includes('account') ||
        h.includes('bank') ||
        h.includes('source') ||
        h.includes('payment mode')
      ) && !h.includes('loan')) {
        colAccount = idx;
      } else if (colCategory === -1 && (h.includes('category') || h.includes('cat')) && !h.includes('sub')) {
        colCategory = idx;
      } else if (colYear === -1 && (h.includes('year') || h === 'yr' || h === 'yyyy')) {
        colYear = idx;
      } else if (colMonth === -1 && (h.includes('month') || h === 'mo' || h === 'mm')) {
        colMonth = idx;
      } else if (colDate === -1 && h.includes('date')) {
        colDate = idx;
      } else if (colProcessed === -1 && (
        h.includes('scanned') ||
        h.includes('budget analysis') ||
        h.includes('process') ||
        h.includes('status')
      )) {
        colProcessed = idx;
      } else if (colCcOffsetTarget === -1 && (
        h.includes('effecting unbilled') ||
        h.includes('affecting unbilled') ||
        h.includes('effect unbilled') ||
        h.includes('affect unbilled') ||
        (h.includes('unbilled') && (h.includes('amount') || h.includes('is'))) ||
        h.includes('offset') ||
        h.includes('target') ||
        h === 'cc offset' ||
        h === 'offset target' ||
        h === 'offset_target'
      )) {
        colCcOffsetTarget = idx;
      }
    }
  }

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
  if (colTaggedCard === -1) colTaggedCard = 14;
  if (colProcessed === -1) colProcessed = 15;
  if (colCcOffsetTarget === -1) colCcOffsetTarget = 16;

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
    colTaggedCard,
    colProcessed,
    colCcOffsetTarget,
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
  const ctx = getCreditCardContext(ss);
  if (!ctx || ctx.values.length < 2) return [];
  const { cols, values } = ctx;
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
