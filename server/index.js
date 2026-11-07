const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

const app = express();
const PORT = process.env.PORT || 3000;
const SPREADSHEET_ID = '1IUekkxG3Au1ZBO5wOkk8Tit6ZZAuLhjiJjlKpTaUg_A';

app.use(cors({ origin: '*' }));
app.use(express.json());

const dbPath = path.join(__dirname, '..', 'data', 'finance-db.json');
const publicDbPath = path.join(__dirname, '..', 'finance-frontend', 'public', 'data', 'finance-db.json');

function getDbData() {
  if (fs.existsSync(dbPath)) {
    return JSON.parse(fs.readFileSync(dbPath, 'utf8'));
  }
  return null;
}

function saveDbData(data) {
  const jsonStr = JSON.stringify(data, null, 2);
  fs.writeFileSync(dbPath, jsonStr, 'utf8');
  if (fs.existsSync(path.dirname(publicDbPath))) {
    fs.writeFileSync(publicDbPath, jsonStr, 'utf8');
  }
}

// 1. Get entire database
app.get('/api/data', (req, res) => {
  const data = getDbData();
  if (!data) return res.status(404).json({ error: 'Database not initialized' });
  res.json(data);
});

// 2. Get transactions with filtering and pagination
app.get('/api/transactions', (req, res) => {
  const data = getDbData();
  if (!data) return res.status(404).json({ error: 'Database not initialized' });

  let results = [...data.transactions];

  const { search, type, account, loan, loanOnly, category, year, month, page = 1, limit = 50 } = req.query;

  if (search) {
    const q = search.toLowerCase();
    results = results.filter(t =>
      (t.subCategory && t.subCategory.toLowerCase().includes(q)) ||
      (t.account && t.account.toLowerCase().includes(q)) ||
      (t.loans && t.loans.toLowerCase().includes(q)) ||
      (t.category && t.category.toLowerCase().includes(q))
    );
  }

  if (loanOnly === 'true') {
    results = results.filter(t => Boolean(t.loans && t.loans.trim()));
  }

  if (loan && loan !== 'All') {
    results = results.filter(t => (t.loans || '').toLowerCase() === loan.toLowerCase());
  }

  if (type && type !== 'All') {
    results = results.filter(t => t.type.toLowerCase() === type.toLowerCase());
  }

  if (account && account !== 'All') {
    results = results.filter(t => t.account.toLowerCase() === account.toLowerCase());
  }

  if (category && category !== 'All') {
    results = results.filter(t => t.category.toLowerCase() === category.toLowerCase());
  }

  if (year && year !== 'All') {
    results = results.filter(t => String(t.year) === String(year));
  }

  if (month && month !== 'All') {
    results = results.filter(t => String(t.month) === String(month));
  }

  const total = results.length;
  const pageNum = parseInt(page, 10);
  const pageSize = parseInt(limit, 10);
  const start = (pageNum - 1) * pageSize;
  const paginated = results.slice(start, start + pageSize);

  res.json({
    total,
    page: pageNum,
    pageSize,
    totalPages: Math.ceil(total / pageSize),
    data: paginated
  });
});

// 3. Add new transaction (strictly Add only, appends to Google Sheet)
app.post('/api/transactions', (req, res) => {
  const tx = req.body;
  if (!tx.date || (tx.credit === undefined && tx.debit === undefined)) {
    return res.status(400).json({ error: 'Invalid transaction payload: date and credit/debit required' });
  }

  const data = getDbData();
  if (!data) return res.status(500).json({ error: 'Database not available' });

  const credit = Number(tx.credit) || 0;
  const debit = Number(tx.debit) || 0;
  const net = credit + debit;
  const parts = tx.date.split('/');
  const year = tx.year || (parts.length === 3 ? parseInt(parts[2], 10) : new Date().getFullYear());
  const month = tx.month || (parts.length >= 2 ? parseInt(parts[1], 10) : new Date().getMonth() + 1);
  const debitWithoutSign = Math.abs(debit);

  const newTx = {
    id: data.transactions.length + 1,
    date: tx.date,
    credit,
    debit,
    net,
    remainder: 0,
    subCategory: tx.subCategory || '',
    type: tx.type || 'Expense',
    loans: tx.loans || '',
    account: tx.account || '',
    category: tx.category || '',
    year,
    month,
    debitWithoutSign,
    scannedForBudgetAnalysis: 'Done'
  };

  // Add to local state
  data.transactions.unshift(newTx);
  data.metadata.counts.transactions = data.transactions.length;
  data.metadata.lastUpdated = new Date().toISOString();

  // Also update account balance if matching account exists
  data.accounts = data.accounts.map(acc => {
    if (acc.name.toLowerCase() === (tx.account || '').toLowerCase()) {
      return {
        ...acc,
        currentBalance: acc.currentBalance + net
      };
    }
    return acc;
  });

  saveDbData(data);

  // Append row directly into Google Sheet using spawn
  // Escape commas in cell values if any
  const esc = (val) => String(val || '').replace(/,/g, '\\,');
  const rowCells = [
    esc(tx.date),
    credit,
    debit,
    net,
    '', // remainder formula/blank
    esc(tx.subCategory),
    esc(tx.type || 'Expense'),
    esc(tx.loans),
    esc(tx.account),
    esc(tx.category),
    year,
    month,
    debitWithoutSign,
    'Done'
  ].join(',');

  const { spawn } = require('child_process');
  const gsheetsProc = spawn('gsheets.cmd', ['mutate', 'append', SPREADSHEET_ID, 'Transactions', rowCells], { shell: true });
  let stdoutData = '';
  let stderrData = '';

  gsheetsProc.stdout.on('data', d => { stdoutData += d.toString(); });
  gsheetsProc.stderr.on('data', d => { stderrData += d.toString(); });

  gsheetsProc.on('close', (code) => {
    const sheetSynced = code === 0;
    const sheetError = code !== 0 ? (stderrData || `CLI exited with code ${code}`) : null;

    if (sheetSynced) {
      console.log('Successfully appended row to Google Sheet Transactions tab:', stdoutData.trim());
    } else {
      console.warn('Note: gsheets append failed with code', code, stderrData);
    }

    res.status(201).json({
      success: true,
      transaction: newTx,
      sheetSynced,
      sheetError,
      message: sheetSynced 
        ? 'Transaction added and appended to Google Sheet successfully!' 
        : 'Transaction added to local database. (Google Sheet sync failed: ' + (sheetError || 'unknown error') + ')'
    });
  });
});

// 4. Update SIP Tracer status in Google Sheet
const SIP_FUND_ROW_MAP = {
  'bandhan small cap': 2,
  'icici pru silver fof': 3,
  'icici pru liquid fund': 4,
  'icici pru liquid fund (ins.)': 5,
  'invesco india mid cap': 6,
  'parag parikh flexi cap': 7,
  'sbi gold fund': 8,
  'bandhan nifty 50 index fund': 9,
  'hsbc value fund': 10
};

app.post('/api/sips/status', (req, res) => {
  const { fundName, targetMonth = 'current', status = 'Paid' } = req.body;
  if (!fundName) {
    return res.status(400).json({ error: 'fundName required' });
  }

  const row = SIP_FUND_ROW_MAP[fundName.toLowerCase().trim()];
  if (!row) {
    return res.status(404).json({ error: `Unknown fund name: ${fundName}` });
  }

  const col = targetMonth === 'next' ? 'H' : 'E';
  const cellRange = `"SIP Tracer!${col}${row}"`;

  // Update in Google Sheet
  const { spawn } = require('child_process');
  const proc = spawn('gsheets.cmd', ['mutate', 'write', SPREADSHEET_ID, cellRange, status], { shell: true });
  let stdoutData = '';
  let stderrData = '';

  proc.stdout.on('data', d => { stdoutData += d.toString(); });
  proc.stderr.on('data', d => { stderrData += d.toString(); });

  proc.on('close', (code) => {
    const sheetSynced = code === 0;

    // Update local database
    const data = getDbData();
    if (data && data.sips) {
      data.sips = data.sips.map(s => {
        if (s.fundName.toLowerCase().trim() === fundName.toLowerCase().trim()) {
          return {
            ...s,
            currentStatus: targetMonth === 'current' ? status : s.currentStatus,
            nextStatus: targetMonth === 'next' ? status : s.nextStatus
          };
        }
        return s;
      });
      data.metadata.lastUpdated = new Date().toISOString();
      saveDbData(data);
    }

    if (sheetSynced) {
      console.log(`Updated SIP Tracer for "${fundName}" to ${status} in Google Sheet (${col}${row})`);
    } else {
      console.warn(`Note: Failed to update SIP Tracer in Google Sheet:`, stderrData);
    }

    res.json({
      success: true,
      sheetSynced,
      message: sheetSynced 
        ? `✓ SIP "${fundName}" status updated to "${status}" in Google Sheet!` 
        : `✓ SIP status saved locally. (Google Sheet sync failed: ${stderrData})`
    });
  });
});

// 4. Add Mutual Fund Transaction
app.post('/api/mutual-funds/transactions', (req, res) => {
  const tx = req.body;
  if (!tx.schemeName || !tx.amount) {
    return res.status(400).json({ error: 'schemeName and amount required' });
  }

  const { spawn } = require('child_process');
  const date = tx.date || new Date().toLocaleDateString('en-GB');
  const amount = Number(tx.amount) || 0;
  const units = Number(tx.units) || 0;
  const nav = Number(tx.nav) || 0;
  const platform = tx.platform || 'Groww';
  const subCategory = tx.subCategory || 'Mutual Fund';
  const folio = tx.folioNumber || '';
  const schemeCode = tx.schemeCode || '';
  const schemeId = tx.schemeId || '';

  const rowCells = `${tx.schemeName},${schemeId},${date},Equity,Mutual Fund,${platform},${subCategory},${folio},${amount},${amount},0,${units},${nav},${nav},0,0,Done,${schemeCode}`;

  const proc = spawn('gsheets.cmd', ['mutate', 'append', SPREADSHEET_ID, 'Mutual Fund Transactions', rowCells], { shell: true });
  let stderrData = '';
  proc.stderr.on('data', d => { stderrData += d.toString(); });

  proc.on('close', (code) => {
    const sheetSynced = code === 0;

    // Update local DB
    const data = getDbData();
    if (data) {
      if (!data.mutualFundTransactions) data.mutualFundTransactions = [];
      const newEntry = {
        id: data.mutualFundTransactions.length + 1,
        schemeName: tx.schemeName,
        schemeId,
        schemeCode,
        date,
        subCategory,
        platform,
        folioNumber: folio,
        amount,
        units,
        nav,
        currentNav: nav,
        processed: 'Done'
      };
      data.mutualFundTransactions.unshift(newEntry);

      // Update holding units/investedValue if matching holding exists
      if (data.holdings) {
        data.holdings = data.holdings.map(h => {
          if (h.schemeName.toLowerCase().trim() === tx.schemeName.toLowerCase().trim()) {
            const newUnits = (h.units || 0) + units;
            const newInvested = (h.investedValue || 0) + amount;
            const newAvgNav = newUnits > 0 ? (newInvested / newUnits) : h.avgBuyingNav;
            return {
              ...h,
              units: newUnits,
              investedValue: newInvested,
              avgBuyingNav: Math.round(newAvgNav * 100) / 100,
              currentValue: Math.round(newUnits * (h.nav || nav) * 100) / 100
            };
          }
          return h;
        });
      }

      data.metadata.lastUpdated = new Date().toISOString();
      saveDbData(data);
    }

    res.json({
      success: true,
      sheetSynced,
      message: sheetSynced
        ? '✓ Mutual Fund transaction appended to Google Sheet!'
        : '✓ Saved in database.'
    });
  });
});

// 5. Add New Mutual Fund Holding
app.post('/api/mutual-funds/holdings', (req, res) => {
  const h = req.body;
  if (!h.schemeName) {
    return res.status(400).json({ error: 'schemeName required' });
  }

  const { spawn } = require('child_process');
  const units = Number(h.units) || 0;
  const avgBuyingNav = Number(h.avgBuyingNav) || 0;
  const investedValue = Number(h.investedValue) || (units * avgBuyingNav);
  const nav = Number(h.nav) || avgBuyingNav;
  const navDate = new Date().toLocaleDateString('en-GB');
  const currentValue = units * nav;

  const rowCells = `${h.schemeName},${h.schemeCode || ''},${h.schemeId || ''},${h.category || 'Equity'},${h.subCategory || 'Small Cap'},${h.savingsType || 'Mutual Fund'},${h.savingsCategory || 'Savings'},${h.source || 'Groww'},${units},${avgBuyingNav},${investedValue},${nav},${navDate},${currentValue},0,0,0,${investedValue},0`;

  const proc = spawn('gsheets.cmd', ['mutate', 'append', SPREADSHEET_ID, 'Mutual Investment Holdings', rowCells], { shell: true });
  let stderrData = '';
  proc.stderr.on('data', d => { stderrData += d.toString(); });

  proc.on('close', (code) => {
    const sheetSynced = code === 0;

    const data = getDbData();
    if (data) {
      if (!data.holdings) data.holdings = [];
      const newHolding = {
        schemeName: h.schemeName,
        schemeCode: h.schemeCode || '',
        schemeId: h.schemeId || '',
        category: h.category || 'Equity',
        subCategory: h.subCategory || 'Small Cap',
        savingsType: h.savingsType || 'Mutual Fund',
        savingsCategory: h.savingsCategory || 'Savings',
        source: h.source || 'Groww',
        units,
        avgBuyingNav,
        investedValue,
        nav,
        navDate,
        currentValue,
        returns: 0,
        absReturn: 0,
        afterTaxReturn: 0,
        previousValue: investedValue,
        oneDayChange: 0,
        oneDayChangePercent: 0
      };
      data.holdings.push(newHolding);
      data.metadata.lastUpdated = new Date().toISOString();
      saveDbData(data);
    }

    res.json({
      success: true,
      sheetSynced,
      message: sheetSynced
        ? '✓ Mutual Fund holding appended to Google Sheet!'
        : '✓ Holding saved locally.'
    });
  });
});

// 6. Live sync from Google Sheet
app.post('/api/sync', (req, res) => {
  const syncScript = path.join(__dirname, '..', 'scripts', 'sync-sheets.js');
  exec(`node "${syncScript}"`, (err, stdout, stderr) => {
    if (err) {
      return res.status(500).json({ error: 'Sync error', details: stderr || err.message });
    }
    const updated = getDbData();
    res.json({ success: true, message: 'Google Sheets synced successfully!', metadata: updated.metadata });
  });
});

app.listen(PORT, () => {
  console.log(`Finance Manager API Server listening on port ${PORT}`);
});
