const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// surge stores token in ~/.surge/credentials
const homeDir = require('os').homedir();
const surgeDir = path.join(homeDir, '.surge');
const credFile = path.join(surgeDir, '.credentials');

// Create surge dir if not exists
if (!fs.existsSync(surgeDir)) {
    fs.mkdirSync(surgeDir, { recursive: true });
}

// First, try to get token via surge API
const https = require('https');

const email = 'levoved2@gamen.me';
const password = 'Qwerty123';

const data = JSON.stringify({
    email: email,
    password: password
});

const options = {
    hostname: 'surge.sh',
    port: 443,
    path: '/token',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': data.length
    }
};

const req = https.request(options, (res) => {
    let body = '';
    res.on('data', (chunk) => body += chunk);
    res.on('end', () => {
        console.log('Response:', res.statusCode, body);
        if (res.statusCode === 200) {
            try {
                const tokenData = JSON.parse(body);
                console.log('Token received!');
                // Write token to surge credentials
                const credContent = `surge.sh ${tokenData.token}\n`;
                fs.writeFileSync(credFile, credContent);
                console.log('Credentials saved to:', credFile);
                
                // Now try to deploy
                try {
                    const result = execSync('surge "D:\\Meter Reading\\Website" dmrs-apk.surge.sh', { 
                        encoding: 'utf-8',
                        timeout: 60000 
                    });
                    console.log('Deploy output:', result);
                } catch (deployErr) {
                    console.log('Deploy error:', deployErr.message);
                    if (deployErr.stdout) console.log('stdout:', deployErr.stdout);
                    if (deployErr.stderr) console.log('stderr:', deployErr.stderr);
                }
            } catch(e) {
                console.log('Parse error:', e.message);
            }
        }
    });
});

req.on('error', (e) => {
    console.log('Request error:', e.message);
});

req.write(data);
req.end();
