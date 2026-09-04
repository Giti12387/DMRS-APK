const { spawn } = require('child_process');

const surge = spawn('surge', ['"D:\\Meter Reading\\Website"', 'dmrs-apk.surge.sh'], {
    shell: true,
    stdio: ['pipe', 'pipe', 'pipe']
});

let step = 0;

surge.stdout.on('data', (data) => {
    const output = data.toString();
    process.stdout.write(output);
    
    if (output.includes('email') && step === 0) {
        step = 1;
        surge.stdin.write('levoved2@gamen.me\n');
    } else if (output.includes('password') && step === 1) {
        step = 2;
        surge.stdin.write('Qwerty123\n');
    }
});

surge.stderr.on('data', (data) => {
    process.stderr.write(data.toString());
});

surge.on('close', (code) => {
    console.log('\nProcess exited with code:', code);
});
