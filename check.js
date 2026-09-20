const fs = require('fs');
const code = fs.readFileSync('D:\\ZoroApps\\admin-panel\\js\\app.js', 'utf8');
try {
  new Function(code);
  console.log('OK');
} catch (e) {
  console.log(e.message);
}
