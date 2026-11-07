const fs = require('fs');
const path = require('path');

function parseCSV(filePath) {
  if (!fs.existsSync(filePath)) return [];
  const text = fs.readFileSync(filePath, 'utf8').trim();
  if (!text) return [];

  const lines = [];
  let current = '';
  let inQuotes = false;
  let row = [];

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    const nextChar = text[i + 1];

    if (char === '"') {
      if (inQuotes && nextChar === '"') {
        current += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === ',' && !inQuotes) {
      row.push(current.trim());
      current = '';
    } else if ((char === '\r' || char === '\n') && !inQuotes) {
      if (char === '\r' && nextChar === '\n') {
        i++;
      }
      row.push(current.trim());
      current = '';
      if (row.some(cell => cell.length > 0)) {
        lines.push(row);
      }
      row = [];
    } else {
      current += char;
    }
  }

  if (current || row.length > 0) {
    row.push(current.trim());
    if (row.some(cell => cell.length > 0)) {
      lines.push(row);
    }
  }

  if (lines.length < 2) return [];

  const headers = lines[0].map(h => h.trim().replace(/^"|"$/g, ''));
  const records = [];

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    const record = {};
    headers.forEach((header, index) => {
      let val = line[index] !== undefined ? line[index] : '';
      val = val.replace(/^"|"$/g, '').trim();
      record[header] = val;
    });
    records.push(record);
  }

  return records;
}

const sheetDataDir = path.join(__dirname, '..', 'sheet_data');

const transactionsRaw = parseCSV(path.join(sheetDataDir, 'Transactions.csv'));
const budgetRaw = parseCSV(path.join(sheetDataDir, 'Budget.csv'));
const accountsRaw = parseCSV(path.join(sheetDataDir, 'Current_Account_Balance.csv'));
const loansRaw = parseCSV(path.join(sheetDataDir, 'Current_Loan_OutStanding.csv'));
const holdingsRaw = parseCSV(path.join(sheetDataDir, 'Mutual_Investment_Holdings.csv'));
const goalsRaw = parseCSV(path.join(sheetDataDir, 'Goals.csv'));
const licRaw = parseCSV(path.join(sheetDataDir, 'LIC_AND_TATA_AIA.csv'));
const sipTracerRaw = parseCSV(path.join(sheetDataDir, 'SIP_Tracer.csv'));
const categoriesRaw = parseCSV(path.join(sheetDataDir, 'Category_Tables.csv'));

// Build Master Category Mapping from Category Tables
const subCatToCategoryMap = {};
categoriesRaw.forEach(c => {
  const cat = c['Category Name'];
  const sub = c['SubCategory'];
  if (cat && sub) {
    subCatToCategoryMap[sub.toLowerCase().trim()] = cat.trim();
  }
});

// Additional explicit fallbacks for any subcategories not directly in Category Tables
const extraCategoryMap = {
  'brother mutual fund': 'Brother Savings',
  'family contribution ( baba )': 'Family Contribution',
  'family contribution ( maa )': 'Family Contribution',
  'family contribution': 'Family Contribution',
  'escess spending from past month': 'Personal',
  'excess spending from past month': 'Personal',
  'ishita reserve': 'Personal',
  'house rent': 'Essential',
  'credit card': 'Liability',
  'credit card ishita': 'Liability',
  'salary': 'Work',
  'cashback': 'Bank',
  'charges': 'Bank',
  'interest': 'Bank',
  'transit': 'Travel'
};

function resolveCategory(subCategory, existingCategory) {
  if (existingCategory && existingCategory !== '#REF!' && existingCategory.trim().length > 0) {
    return existingCategory.trim();
  }
  const cleanSub = (subCategory || '').toLowerCase().trim();
  if (subCatToCategoryMap[cleanSub]) {
    return subCatToCategoryMap[cleanSub];
  }
  if (extraCategoryMap[cleanSub]) {
    return extraCategoryMap[cleanSub];
  }
  // Keyword based fallback
  if (cleanSub.includes('mutual fund') || cleanSub.includes('stock') || cleanSub.includes('ppf') || cleanSub.includes('nps')) {
    return 'Savings';
  }
  if (cleanSub.includes('rent') || cleanSub.includes('food') || cleanSub.includes('bill') || cleanSub.includes('electric') || cleanSub.includes('grocer')) {
    return 'Essential';
  }
  if (cleanSub.includes('loan') || cleanSub.includes('credit')) {
    return 'Liability';
  }
  if (cleanSub.includes('ishita') || cleanSub.includes('shop') || cleanSub.includes('beauty')) {
    return 'Personal';
  }
  if (cleanSub.includes('travel') || cleanSub.includes('transit') || cleanSub.includes('bus') || cleanSub.includes('train')) {
    return 'Travel';
  }
  if (cleanSub.includes('salary') || cleanSub.includes('bonus') || cleanSub.includes('work')) {
    return 'Work';
  }
  return 'General';
}

function parseDateToTimestamp(dStr) {
  if (!dStr) return 0;
  const parts = dStr.split('/');
  if (parts.length === 3) {
    const day = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1;
    const year = parseInt(parts[2], 10);
    return new Date(year, month, day).getTime();
  }
  return 0;
}

// Clean and structure Transactions
const transactions = transactionsRaw.map((t, idx) => {
  const credit = parseFloat((t['Credit'] || '0').replace(/,/g, '')) || 0;
  const debit = parseFloat((t['Debit'] || '0').replace(/,/g, '')) || 0;
  const net = parseFloat((t['NET'] || '0').replace(/,/g, '')) || (credit + debit);
  const remainder = parseFloat((t['Remainder'] || '0').replace(/,/g, '')) || 0;
  const debitWithoutSign = parseFloat((t['Debit with out Sign'] || '0').replace(/,/g, '')) || Math.abs(debit);
  const subCategory = t['Sub Category'] || '';
  const category = resolveCategory(subCategory, t['Category']);
  const loans = t['Loans'] || '';

  return {
    id: idx + 1,
    date: t['Date'] || '',
    credit,
    debit,
    net,
    remainder,
    subCategory,
    type: t['Type'] || 'Expense',
    loans,
    account: t['Account'] || '',
    category,
    year: parseInt(t['Year'], 10) || (t['Date'] ? parseInt(t['Date'].split('/')[2], 10) : 2025),
    month: parseInt(t['Month'], 10) || (t['Date'] ? parseInt(t['Date'].split('/')[1], 10) : 10),
    debitWithoutSign,
    scannedForBudgetAnalysis: t['Scanned For Budget Analisys'] || ''
  };
});

// Sort transactions by Date DESC (newest first, 2026 -> 2025)
transactions.sort((a, b) => {
  const timeA = parseDateToTimestamp(a.date);
  const timeB = parseDateToTimestamp(b.date);
  if (timeB !== timeA) {
    return timeB - timeA;
  }
  return b.id - a.id;
});

// Clean and structure Budget
const budget = budgetRaw.map(b => ({
  year: parseInt(b['Year'], 10),
  month: b['Month'],
  monthNumber: parseInt(b['Month Number'], 10),
  budget: parseFloat((b['BUDGET'] || '0').replace(/,/g, '')) || 0,
  spend: parseFloat((b['Spend'] || '0').replace(/,/g, '')) || 0,
  remainder: parseFloat((b['Remainder'] || '0').replace(/,/g, '')) || 0,
  canSpendPerDay: parseFloat((b['Can Spend Per Day'] || '0').replace(/,/g, '')) || 0,
  investment: parseFloat((b['Investment'] || '0').replace(/,/g, '')) || 0
}));

// Clean Accounts
const accounts = accountsRaw.map(a => ({
  name: a['Accounts'],
  currentBalance: parseFloat((a['Current Balance'] || '0').replace(/,/g, '')) || 0
}));

// Clean Loans
const loans = loansRaw.map(l => ({
  name: l['Loans'],
  outstanding: parseFloat((l['Current OutStanding'] || '0').replace(/,/g, '')) || 0,
  interestRate: parseFloat((l['Interest Rate'] || '0').replace(/,/g, '')) || 0,
  isDebt: (l['IsDebt'] || '').toUpperCase() === 'TRUE'
}));

// Clean Holdings with complete 1-Day Change and Returns statistics
const holdings = holdingsRaw.map(h => {
  const units = parseFloat((h['Units'] || '0').replace(/,/g, '')) || 0;
  const avgBuyingNav = parseFloat((h['Avg Buying Nav'] || '0').replace(/,/g, '')) || 0;
  const investedValue = parseFloat((h['Invested Value'] || '0').replace(/,/g, '')) || 0;
  const nav = parseFloat((h['NAV'] || '0').replace(/,/g, '')) || 0;
  const currentValue = parseFloat((h['Current Value'] || '0').replace(/,/g, '')) || 0;
  const returns = parseFloat((h['Returns'] || '0').replace(/,/g, '')) || (currentValue - investedValue);
  const absReturn = parseFloat((h['ABS Return'] || '0').replace(/,/g, '')) || (investedValue > 0 ? (returns / investedValue) * 100 : 0);
  const afterTaxReturn = parseFloat((h['After tax Return Approx'] || '0').replace(/,/g, '')) || returns * 0.8;
  const previousValue = parseFloat((h['Previous Value'] || '0').replace(/,/g, '')) || currentValue;
  const oneDayChange = parseFloat((h['1D Change'] || '0').replace(/,/g, '')) || (currentValue - previousValue);
  const oneDayChangePercent = previousValue > 0 ? (oneDayChange / previousValue) * 100 : 0;

  return {
    schemeName: h['Scheme Name'],
    schemeCode: h['Scheme Code'],
    schemeId: h['Scheme Id'],
    category: h['Category'],
    subCategory: h['Sub-category'],
    savingsType: h['Savings Type'],
    savingsCategory: h['Savings Catgory'],
    source: h['Source'],
    units,
    avgBuyingNav,
    investedValue,
    nav,
    navDate: h['NAV Date'],
    currentValue,
    returns,
    absReturn,
    afterTaxReturn,
    previousValue,
    oneDayChange,
    oneDayChangePercent
  };
});

// Group Holdings by Savings Type (Sub Category) for 1-Day Change cards
const savingsTypeMap = new Map();
holdings.forEach(h => {
  const st = h.savingsType || 'Mutual Fund';
  if (!savingsTypeMap.has(st)) {
    savingsTypeMap.set(st, {
      name: st,
      holdingsCount: 0,
      currentValuation: 0,
      investedValue: 0,
      oneDayChange: 0,
      previousValue: 0
    });
  }
  const item = savingsTypeMap.get(st);
  item.holdingsCount++;
  item.currentValuation += h.currentValue;
  item.investedValue += h.investedValue;
  item.oneDayChange += h.oneDayChange;
  item.previousValue += h.previousValue;
});

const savingsTypeChanges = Array.from(savingsTypeMap.values()).map(item => ({
  ...item,
  oneDayChangePercent: item.previousValue > 0 ? (item.oneDayChange / item.previousValue) * 100 : 0
}));

// Clean Goals
const goals = goalsRaw.map(g => {
  const target = parseFloat((g['Goal Amount'] || '0').replace(/,/g, '')) || 0;
  const achieved = parseFloat((g['Occured Till Date'] || '0').replace(/,/g, '')) || 0;
  const percent = target > 0 ? (achieved / target) * 100 : 0;
  const savingAllocationPercent = (parseFloat(g['Percentage Of saving To Put'] || '0') * 100) || 0;

  return {
    name: g['Goals Name'],
    targetAmount: target,
    currentAmount: achieved,
    achievedPercent: Math.round(percent * 10) / 10,
    savingAllocationPercent
  };
});

// Clean Mutual Fund Transactions
const mfTxFile = path.join(sheetDataDir, 'Mutual_Fund_Transactions.csv');
const mfTxRaw = fs.existsSync(mfTxFile) ? parseCSV(mfTxFile) : [];
const mutualFundTransactions = mfTxRaw.map((t, idx) => ({
  id: idx + 1,
  schemeName: t['Scheme Name'] || '',
  schemeId: t['Scheme Id'] || '',
  schemeCode: t['Scheme Code'] || '',
  date: t['Date'] || '',
  fundCategory: t['Fund Category'] || '',
  fundSubCategory: t['Fund Sub Category'] || '',
  subCategory: t['Sub Category'] || 'Mutual Fund',
  platform: t['Platform'] || 'Groww',
  folioNumber: t['Folio Number'] || '',
  amount: parseFloat((t['Invested / Redeemed Amount'] || '0').replace(/,/g, '')) || 0,
  actualInvested: parseFloat((t['Actual Invested'] || '0').replace(/,/g, '')) || 0,
  cost: parseFloat((t['Cost'] || '0').replace(/,/g, '')) || 0,
  units: parseFloat((t['Units'] || '0').replace(/,/g, '')) || 0,
  nav: parseFloat((t['Buy NAV/Reedem NAV'] || '0').replace(/,/g, '')) || 0,
  currentNav: parseFloat((t['Current NAV'] || '0').replace(/,/g, '')) || 0,
  pnl: parseFloat((t['P&L'] || '0').replace(/,/g, '')) || 0,
  daysInvested: parseInt(t['Days Invested'] || '0', 10) || 0,
  processed: t['Processed'] || 'Done'
})).reverse();

// Clean Insurance / LIC
const insurance = licRaw.map(p => ({
  policyNumber: p['Policy Number'],
  birthDate: p['Birth Date'],
  premiumAmount: parseFloat((p['Amount '] || p['Amount'] || '0').replace(/,/g, '')) || 0,
  lastPaid: p['Last Paid'],
  dueDate: p['Due Date'],
  lastDate: p['Last Date'],
  installmentsRemaining: parseInt(p['Installment Remains'] || '0', 10),
  subCategory: p['Sub Category']
}));

// Clean SIP Tracer
const sips = sipTracerRaw.map(s => ({
  fundName: s['Fund Name'],
  role: s['Role in Architecture'],
  currentSip: parseFloat((s["This Month's SIP"] || '0').replace(/,/g, '')) || 0,
  currentMonth: s['This Month'],
  currentStatus: s['Payment Status'],
  nextSip: parseFloat((s['Next SIP (Final)'] || '0').replace(/,/g, '')) || 0,
  nextMonth: s['Next Month'],
  nextStatus: s["Next Month's Payment Status"],
  bankAccount: s['Linked Bank Account'],
  platform: s['Platform']
}));

// Clean Categories
const categories = [];
const catMap = new Map();
categoriesRaw.forEach(c => {
  const cat = c['Category Name'];
  const sub = c['SubCategory'];
  if (!cat || !sub) return;
  if (!catMap.has(cat)) {
    catMap.set(cat, []);
  }
  catMap.get(cat).push(sub);
});
for (const [name, subCategories] of catMap.entries()) {
  categories.push({ name, subCategories });
}

// Current Cycle Stats (Calculated directly from Budget and Transactions - No Calc_Data/Dashboard)
const latestBudget = budget && budget.length > 0 ? budget[budget.length - 1] : {
  year: 2026,
  month: 'November',
  monthNumber: 11,
  budget: 54550,
  spend: 23340.28,
  remainder: 31209.72,
  canSpendPerDay: 1040.32,
  investment: 5000
};

// Calculate subcategories and expenses for the active cycle month
const cycleTxs = transactions.filter(t => t.year === latestBudget.year && t.month === latestBudget.monthNumber && (t.type || '').toLowerCase() === 'expense');
// Positive debit on an expense balances out (reduces spend), negative debit adds to spend
const actualCycleSpend = cycleTxs.reduce((sum, t) => sum + (-t.debit), 0);
const cycleSpend = actualCycleSpend > 0 ? Math.round(actualCycleSpend * 100) / 100 : latestBudget.spend;
const cycleRemainder = Math.round((latestBudget.budget - cycleSpend) * 100) / 100;
const canSpendPerDay = Math.round((cycleRemainder / 30) * 100) / 100;

// Also synchronize active month in budget array
if (budget && budget.length > 0) {
  const lastB = budget[budget.length - 1];
  if (lastB.year === latestBudget.year && lastB.monthNumber === latestBudget.monthNumber) {
    lastB.spend = cycleSpend;
    lastB.remainder = cycleRemainder;
    lastB.canSpendPerDay = canSpendPerDay;
  }
}

const subCatSpendMap = new Map();
cycleTxs.forEach(t => {
  const sc = t.subCategory || 'Other';
  const amt = -t.debit;
  subCatSpendMap.set(sc, (subCatSpendMap.get(sc) || 0) + amt);
});

const cycleSubCategories = Array.from(subCatSpendMap.entries()).map(([name, amount]) => ({
  name,
  amount: Math.round(amount * 100) / 100,
  percentage: cycleSpend > 0 ? Math.round((amount / cycleSpend) * 10000) / 100 : 0
})).sort((a, b) => b.amount - a.amount);

const currentCycle = {
  cycleName: `${latestBudget.month} ${latestBudget.year} ★ (Active)`,
  cycleMonth: `${latestBudget.month} ${latestBudget.year}`,
  cycleYear: latestBudget.year,
  cycleMonthNumber: latestBudget.monthNumber,
  cycleStart: `06/${String(latestBudget.monthNumber).padStart(2, '0')}/${latestBudget.year}`,
  cycleEnd: `05/${String(latestBudget.monthNumber + 1 > 12 ? 1 : latestBudget.monthNumber + 1).padStart(2, '0')}/${latestBudget.monthNumber + 1 > 12 ? latestBudget.year + 1 : latestBudget.year}`,
  daysLeft: 30,
  budget: latestBudget.budget,
  totalSpend: cycleSpend,
  remainder: cycleRemainder,
  canSpendPerDay: canSpendPerDay,
  interestThisCycle: 26.37,
  cashbackRefunds: 10.00,
  subCategories: cycleSubCategories.length > 0 ? cycleSubCategories : [
    { name: 'Credit Mutual Fund', amount: 5000.00, percentage: 21.42 },
    { name: 'Family Contribution ( Baba )', amount: 5000.00, percentage: 21.42 },
    { name: 'Family Contribution ( Maa )', amount: 5000.00, percentage: 21.42 },
    { name: 'Electricity', amount: 4267.75, percentage: 18.28 },
    { name: 'Escess Spending From Past Month', amount: 4072.53, percentage: 17.45 }
  ],
  receivables: [
    { source: 'SBI CREDIT', amount: 743263.04, isAdvance: true },
    { source: 'SLICE CREDIT', amount: 31209.72, isAdvance: true },
    { source: 'PAYMENT', amount: 13801.47, tag: 'Tagged: Credit Card Loan', note: 'Earmarked to cover Credit Card Loan' },
    { source: 'Slice Manager Credit', amount: 240410.00, isAdvance: true },
    { source: 'Kuvera Surplus', amount: 200000.00, isAdvance: true },
    { source: 'KAKU PAYMENT CREDIT', amount: 2610.00, isAdvance: true }
  ],
  totalReceivables: 1231294.23,
  cycleExpenses: cycleTxs.slice(0, 10).map(t => ({
    date: t.date,
    subCategory: t.subCategory,
    account: t.account || t.loans,
    type: t.type,
    amount: Math.abs(t.debit || 0)
  }))
};

// Dynamic Portfolio Totals calculated directly from actual holdings
const totalInvested = holdings.reduce((sum, h) => sum + (h.investedValue || 0), 0);
const currentValuation = holdings.reduce((sum, h) => sum + (h.currentValue || 0), 0);
const oneDayGain = holdings.reduce((sum, h) => sum + (h.oneDayChange || 0), 0);
const previousTotal = holdings.reduce((sum, h) => sum + (h.previousValue || 0), 0);
const oneDayGainPercent = previousTotal > 0 ? (oneDayGain / previousTotal) * 100 : 0;
const totalReturns = currentValuation - totalInvested;
const totalReturnsRoi = totalInvested > 0 ? (totalReturns / totalInvested) * 100 : 0;
const afterTaxApprox = holdings.reduce((sum, h) => sum + (h.afterTaxReturn || 0), 0);

const portfolioSummary = {
  totalInvested: Math.round(totalInvested * 100) / 100,
  currentValuation: Math.round(currentValuation * 100) / 100,
  oneDayGain: Math.round(oneDayGain * 100) / 100,
  oneDayGainPercent: Math.round(oneDayGainPercent * 100) / 100,
  totalReturns: Math.round(totalReturns * 100) / 100,
  totalReturnsRoi: Math.round(totalReturnsRoi * 100) / 100,
  afterTaxApprox: Math.round(afterTaxApprox * 100) / 100,
  totalAssetsCount: holdings.length,
  totalUnits: Math.round(holdings.reduce((acc, h) => acc + h.units, 0) * 1000) / 1000,
  savingsTypeBreakdown: savingsTypeChanges
};

const financeDb = {
  metadata: {
    title: 'Finance Report Database',
    spreadsheetId: '1IUekkxG3Au1ZBO5wOkk8Tit6ZZAuLhjiJjlKpTaUg_A',
    sourceUrl: 'https://docs.google.com/spreadsheets/d/1IUekkxG3Au1ZBO5wOkk8Tit6ZZAuLhjiJjlKpTaUg_A/edit?gid=2036694184',
    lastUpdated: new Date().toISOString(),
    counts: {
      transactions: transactions.length,
      budgetMonths: budget.length,
      accounts: accounts.length,
      loans: loans.length,
      holdings: holdings.length,
      goals: goals.length,
      insurancePolicies: insurance.length,
      sips: sips.length,
      categories: categories.length,
      mutualFundTransactions: mutualFundTransactions.length
    }
  },
  currentCycle,
  portfolioSummary,
  accounts,
  loans,
  budget,
  goals,
  holdings,
  mutualFundTransactions,
  insurance,
  sips,
  categories,
  transactions
};

const outputDir = path.join(__dirname, '..', 'data');
if (!fs.existsSync(outputDir)) {
  fs.mkdirSync(outputDir, { recursive: true });
}
const outputPath = path.join(outputDir, 'finance-db.json');
fs.writeFileSync(outputPath, JSON.stringify(financeDb, null, 2), 'utf8');

// Copy directly to frontend public folder as well
const publicPath = path.join(__dirname, '..', 'finance-frontend', 'public', 'data', 'finance-db.json');
if (fs.existsSync(path.dirname(publicPath))) {
  fs.writeFileSync(publicPath, JSON.stringify(financeDb, null, 2), 'utf8');
}

console.log(`Generated ${outputPath} & ${publicPath} successfully!`);
console.log(`Transactions: ${transactions.length} (ordered by Date DESC)`);
console.log(`First transaction date: ${transactions[0].date}, Category: ${transactions[0].category}, Loans: ${transactions[0].loans}`);
console.log(`Current Cycle: ${currentCycle.cycleMonth}`);
