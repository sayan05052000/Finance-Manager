const fs = require('fs');
const path = require('path');

const angularJsonPath = path.join(__dirname, '..', 'finance-frontend', 'angular.json');
const content = JSON.parse(fs.readFileSync(angularJsonPath, 'utf8'));

const buildTarget = content.projects['finance-frontend'].architect.build;

buildTarget.options.optimization = {
  fonts: false
};

buildTarget.configurations.production.optimization = {
  scripts: true,
  styles: {
    minify: true,
    inlineCritical: false
  },
  fonts: false
};

buildTarget.configurations.production.budgets = [
  {
    type: 'initial',
    maximumWarning: '1.5MB',
    maximumError: '2.5MB'
  },
  {
    type: 'anyComponentStyle',
    maximumWarning: '40kB',
    maximumError: '60kB'
  }
];

fs.writeFileSync(angularJsonPath, JSON.stringify(content, null, 2), 'utf8');
console.log('Patched angular.json successfully!');
