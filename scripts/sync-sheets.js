const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const SPREADSHEET_ID = '1IUekkxG3Au1ZBO5wOkk8Tit6ZZAuLhjiJjlKpTaUg_A';
const sheetDataDir = path.join(__dirname, '..', 'sheet_data');

if (!fs.existsSync(sheetDataDir)) {
  fs.mkdirSync(sheetDataDir, { recursive: true });
}

const sheets = [
  'Transactions',
  'Category Tables',
  'Current Account Balance',
  'Account Opening Balance',
  'Current Loan OutStanding',
  'Loans Opening Balance',
  'Budget',
  'Goals',
  'Mutual Investment Holdings',
  'Mutual Fund Transactions',
  'LIC AND TATA AIA',
  'Expense Tracker',
  'SIP Tracer',
  'Transaction Types'
];

console.log('Fetching latest sheets from Google Sheets (ID: ' + SPREADSHEET_ID + ')...');

for (const s of sheets) {
  const safeName = s.replace(/ /g, '_');
  const outFile = path.join(sheetDataDir, `${safeName}.csv`);
  try {
    console.log(`- Exporting "${s}"...`);
    execSync(`gsheets readonly export ${SPREADSHEET_ID} "${outFile}" --sheet "${s}"`, { stdio: 'inherit' });
  } catch (err) {
    console.warn(`Warning: Could not export ${s}:`, err.message);
  }
}

console.log('Parsing CSVs into unified JSON...');
execSync(`node "${path.join(__dirname, 'parse-data.js')}"`, { stdio: 'inherit' });

// Also copy to Angular assets if it exists
const angularAssetsDataDir = path.join(__dirname, '..', 'finance-frontend', 'src', 'assets', 'data');
if (!fs.existsSync(angularAssetsDataDir)) {
  fs.mkdirSync(angularAssetsDataDir, { recursive: true });
}
const dbSource = path.join(__dirname, '..', 'data', 'finance-db.json');
const dbTarget = path.join(angularAssetsDataDir, 'finance-db.json');
if (fs.existsSync(dbSource)) {
  fs.copyFileSync(dbSource, dbTarget);
  console.log(`Copied finance-db.json to ${dbTarget}`);
}

console.log('Sync complete!');
